# Resume Point 2a — Transactional Reconciliation (Malaysia)

> **Resume bullet:** "Shipped the Transactional Reconciliation feature in Malaysia,
> reducing data inconsistency by 57%."

This document explains the full Transactional Reconciliation feature end-to-end: the business
problem, the data model, the BFF flow (summary + list), the report-generation flow (CSV export),
the MongoDB aggregation queries, and the configuration.

---

## 1. Business problem

In Malaysia e-invoicing, every invoice a taxpayer submits to ClearTax is also reported to
**LHDN** (Lembaga Hasil Dalam Negeri — Inland Revenue Board of Malaysia). Data can drift between:

- **`fieldValueCt`** — the value ClearTax has (from the customer's uploaded file/ERP), and
- **`fieldValueLhdn`** — the value actually reported to / stored at LHDN.

Transactional Reconciliation surfaces **field-level mismatches** (both at document level and line
level) so customers can spot and fix inconsistencies before they become compliance problems.
This is what drives the "reduced data inconsistency by 57%" outcome.

---

## 2. Report type & template type

- **`ReportType.EINVOICE_MY_TRANSACTIONAL_RECON`** — used by the report-generation (CSV export) flow.
- **`TemplateType.EINVOICE_MY_TRANSACTIONAL_RECON`** — used by the BFF (viewSummary / viewList) flow.

Both are registered in the respective enums:

```java
// data-read-service/.../enums/TemplateType.java
public enum TemplateType {
    EINVOICE_MY_SALES,
    EINVOICE_MY_PURCHASE,
    ...
    EINVOICE_MY_TRANSACTIONAL_RECON;
}
```

---

## 3. Data model — `TransactionalReconDataMismatch`

A recon document (one per invoice/group) contains two kinds of mismatches:

- **Document-level mismatches:** `documentLevelMisMatches.fieldLevelMisMatchList[]`
  — each entry has `fieldName`, `valueInFile` (CT), `valueAtLhdn`.
- **Line-level mismatches:** `lineLevelMisMatches[]`
  — each has `serialNumber`, `productDescription`, and its own `fieldLevelMisMatchList[]`.

Top-level fields used for output: `fileName`, `documentNumber`, `documentDate`, `documentType`,
`uuid` (IRBM Unique Identifier), plus `groupId`, `taxpayerOrgId`, `branchOrgId` for filtering.

---

## 4. BFF flow — `EinvoiceTransactionalReconReadService`

File: `einvoice-data-read-service/.../impl/EinvoiceTransactionalReconReadService.java`
Registered with `@DataReadService(templatesSupported = "einvoice_my_transactional_recon")`.

It loads its field-mapping configs in the constructor (config-driven, see section 7):

```java
public EinvoiceTransactionalReconReadService() throws IOException {
    this.ctFieldDbPathMap = ... "MY/EINVOICE_MY_TRANSACTIONAL_RECON/EinvoiceMyFieldWithDBPath.json";
    this.ctFieldDbPathMapOptionsMapping = ... "MY/EINVOICE_MY_TRANSACTIONAL_RECON/EinvoiceMyFieldWithDbOptions.json";
    this.nodeTypeDbPathMap = ... "MY/EINVOICE_MY_TRANSACTIONAL_RECON/NodeTypeDBPathMapping.json";
}
```

### 4.1 `viewSummary` — dashboard KPI cards

This is the most involved method. It composes counts across **purchase** invoices and
**recon** mismatches:

1. Find the relevant `groupId`s for the accessible hierarchy (purchase collection aggregation).
2. Count total received invoices and "VALID" e-invoices for those groups.
3. Count mismatches from the recon collection using a dedicated mismatch aggregation.
4. Return a `DocumentCountSummaryResponse` with:
   - `totalIngCount` — number of ingested groups
   - `receivedInvoiceCount` — total invoices
   - `eInvoiceGeneratedCount` — count with status VALID
   - `eInvoiceMismatchCount` — total mismatch count

```java
summary.put("totalIngCount", groupIdsList.size());
summary.put("receivedInvoiceCount", totalCount);
summary.put("eInvoiceGeneratedCount", validCount);
summary.put("eInvoiceMismatchCount",
        (mismatchReconDocs != null && !mismatchReconDocs.isEmpty())
                ? mismatchReconDocs.get(0).getInteger("totalMismatchCount") : 0);
```

### 4.2 `viewList` — flattened mismatch table

The UI shows one row per mismatched field. Since a document can have many document-level and
line-level field mismatches, the service **flattens** the nested structure into a flat list of
rows:

```java
// document-level mismatches → one row each
documentModel.getDocumentLevelMisMatches().getFieldLevelMisMatchList().forEach(docLevel -> {
    Document row = new Document(baseDocument);
    row.append("serialNumber", "");
    row.append("productDescription", "");
    row.append("fieldName", docLevel.getFieldName());
    row.append("valueInFile", docLevel.getValueInFile());   // CT value
    row.append("valueAtLhdn", docLevel.getValueAtLhdn());   // LHDN value
    flattenedDocumentListRecon.add(row);
});

// line-level mismatches → one row per field mismatch, carrying line context
documentModel.getLineLevelMisMatches().forEach(line -> {
    line.getFieldLevelMisMatchList().forEach(fLm -> {
        Document row = new Document(lineLevelDocument);      // has serialNumber + productDescription
        row.append("fieldName", fLm.getFieldName());
        row.append("valueInFile", fLm.getValueInFile());
        row.append("valueAtLhdn", fLm.getValueAtLhdn());
        flattenedDocumentListRecon.add(row);
    });
});
```

The flat rows are mapped to `DocumentSummaryResponseDTO` via `einvoiceMapper.toViewDocumentDto`
using schema names `DB_SOURCE_SCHEMA = "EINVOICE_TRANS_RECON_DB_SCHEMA"` →
`LITE_VIEW_SCHEMA = "TRANS_RECON_LITE_VIEW_SCHEMA"`.

### 4.3 `viewDetailed` — intentionally unsupported

```java
@Override
public Flux<DocumentSummaryResponseDTO> viewDetailed(...) {
    throw new NoSuchElementException("View Detailed is not implemented for Transactional Recon Read data service");
}
```

Recon only needs summary + list views, so detailed is deliberately not implemented.

---

## 5. Report-generation flow — `MyTransactionalReconDataSource`

File: `read-data-source/.../einvoicemy/recon/MyTransactionalReconDataSource.java`
Registered with `@DataSourceGenerator(reportTypes = {ReportType.EINVOICE_MY_TRANSACTIONAL_RECON}, version = V1)`
and gated by `@ConditionalOnExpression(...MODE_INIT_CONDITIONAL_EINVOICE_MY)`.

It extends `ReportHelper<TransactionalReconDataMismatch>` and overrides:

### 5.1 `extractDataStream` — read mismatches

```java
@Override
public Flux<TransactionalReconDataMismatch> extractDataStream(HarvesterDataRequest request) {
    Criteria criteria = super.getQueryFromRequest(request);
    Aggregation aggregation = myReconDataSourceHelper.getAggregationOperationFromRequest(criteria);
    return einvoiceMyTransactionalReconRepository.findByAggregation(aggregation)
        .flatMap(result -> Flux.fromIterable(result.getDocuments()));
}
```

### 5.2 `transformDataStream` — mismatch → Avro records (one row per field mismatch)

```java
@Override
public Flux<GenericData.Record> transformDataStream(Schema schema, HarvesterDataRequest req,
                                                     TransactionalReconDataMismatch document) {
    List<GenericData.Record> records = Lists.newArrayList();
    // document-level
    if (document.getDocumentLevelMisMatches() != null) {
        document.getDocumentLevelMisMatches().getFieldLevelMisMatchList().forEach(m ->
            myReconDataSourceHelper.addFieldToRecord(schema, document, records, null,
                m.getFieldName(), m.getValueInFile(), m.getValueAtLhdn()));
    }
    // line-level
    if (CollectionUtils.isNotEmpty(document.getLineLevelMisMatches())) {
        document.getLineLevelMisMatches().forEach(line -> {
            if (CollectionUtils.isNotEmpty(line.getFieldLevelMisMatchList())) {
                line.getFieldLevelMisMatchList().forEach(m ->
                    myReconDataSourceHelper.addFieldToRecord(schema, document, records, line,
                        m.getFieldName(), m.getValueInFile(), m.getValueAtLhdn()));
            }
        });
    }
    return Flux.fromIterable(records);
}
```

### 5.3 Access-control filter (`getAdditionalFilterFromRequest`)

Recon respects the business-hierarchy node type:

```java
if (NodeType.TIN.equals(nodeType)) {
    criteriaList.add(Criteria.where("taxpayerOrgId").in(request.getNodeIds()));
} else if (NodeType.BRANCH_L2.equals(nodeType)) {
    if (StringUtils.isNotBlank(taxPayerOrgId))
        criteriaList.add(Criteria.where("taxpayerOrgId").in(List.of(taxPayerOrgId)));
    criteriaList.add(Criteria.where("branchOrgId").in(request.getNodeIds()));
} else {
    throw new IllegalArgumentException("Invalid node type: " + nodeType);
}
```

### 5.4 File naming

CSV filename uses a prefix + UUID + timestamp; inside-zip name uses the taxpayer number when
present:

```java
return MY_TRANSACTIONAL_RECON_CSV_FILE_NAME_PREFIX
        .concat(supplier_identifier).concat("_").concat(getCurrentDate("ddMM"));
```

### 5.5 Recon helper — `MyReconDataSourceHelper.addFieldToRecord`

Builds each Avro `GenericData.Record` row with the recon columns:

```java
record.put("fileName", document.getFileName());
record.put("documentNumber", document.getDocumentNumber());
record.put("documentDate", documentDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
record.put("documentType", document.getDocumentType());
record.put("irbmUniqueIdentifier", document.getUuid());
record.put("fieldName", fieldName);
record.put("fieldValueCt", fieldValueCt.toString());     // ClearTax value
record.put("fieldValueLhdn", fieldValueLhdn.toString()); // LHDN value
// line context (if line-level mismatch)
record.put("lineItemSerialNumber", lineLevelMisMatch.getSerialNumber());
record.put("productDescription", lineLevelMisMatch.getProductDescription());
```

The grouping aggregation pushes whole documents per `groupId`:

```java
Aggregation.group("groupId").push("$$ROOT").as("documents");
```

---

## 6. MongoDB queries — the mismatch aggregation

The BFF mismatch count is built in `EinvoiceQueryBuilder.getAggregateForViewMismatch`. It matches
documents that have **any** non-empty document-level OR line-level field mismatch, then counts:

```java
Aggregation.match(new Criteria().orOperator(
    new Criteria().andOperator(
        Criteria.where("documentLevelMisMatches.fieldLevelMisMatchList").ne(null),
        Criteria.where("documentLevelMisMatches.fieldLevelMisMatchList").not().size(0)),
    Criteria.where("lineLevelMisMatches").elemMatch(new Criteria().andOperator(
        Criteria.where("fieldLevelMisMatchList").ne(null),
        Criteria.where("fieldLevelMisMatchList").not().size(0)))
));
Aggregation.group().count().as("totalMismatchCount");
```

---

## 7. Repositories (reactive Mongo)

Two repositories serve recon on two paths:

**BFF path** — `einvoice-data-read-service/.../repository/EinvoiceMyTransactionalReconRepository.java`
(uses `@Qualifier("tenantReactiveMongoTemplate")`):

```java
public Flux<Document> getDocumentCountSummary(TypedAggregation<Document> aggregation) {
    return reactiveMongoTemplate.aggregate(aggregation, TransactionalReconDataMismatch.class, Document.class);
}
public Flux<TransactionalReconDataMismatch> getDocumentsDetailed(TypedAggregation<Document> aggregation) {
    aggregation.withOptions(AggregationOptions.builder().allowDiskUse(true).build());
    return reactiveMongoTemplate.aggregate(aggregation, TransactionalReconDataMismatch.class,
            TransactionalReconDataMismatch.class);
}
```

**Report path** — `harvester-core/.../repository/einvoiceMy/EinvoiceMyTransactionalReconRepositoryImpl.java`
(uses the mode-specific template qualifier, and returns a grouped projection):

```java
@ConditionalOnExpression(ModeAwareMongoTemplateMapping.MODE_INIT_CONDITIONAL_EINVOICE_MY)
public class EinvoiceMyTransactionalReconRepositoryImpl implements EinvoiceMyTransactionalReconRepository {
    @Qualifier(ModeAwareMongoTemplateMapping.MONGO_TEMPLATE_MODE_EINVOICE_MY)
    ReactiveMongoTemplate reactiveMongoTemplate;

    public Flux<GroupedTransactionalReconData> findByAggregation(Aggregation aggregation) {
        return reactiveMongoTemplate.aggregate(aggregation, TransactionalReconDataMismatch.class,
                GroupedTransactionalReconData.class);
    }
}
```

---

## 8. Output schema & config (config-driven)

`read-data-source/src/main/resources/mode/EINVOICE_MY/EINVOICE_MY_TRANSACTIONAL_RECON/`

**`output-schema.json`** — the CSV columns:

```json
{
  "type": "record",
  "name": "EINVOICE_MY_TRANSACTIONAL_RECON",
  "fields": [
    {"name": "fileName", "type": ["null", "string"]},
    {"name": "documentNumber", "type": ["null", "string"]},
    {"name": "documentDate", "type": ["null", "string"]},
    {"name": "documentType", "type": ["null", "string"]},
    {"name": "irbmUniqueIdentifier", "type": ["null", "string"]},
    {"name": "lineItemSerialNumber", "type": ["null", "string"]},
    {"name": "productDescription", "type": ["null", "string"]},
    {"name": "fieldName", "type": ["null", "string"]},
    {"name": "fieldValueCt", "type": ["null", "string"]},
    {"name": "fieldValueLhdn", "type": ["null", "string"]}
  ]
}
```

**`report-config.json`** — filter field→DB path + CSV headers:

```json
{
  "filterConfig": {
    "ctFieldDbPathMapping": {
      "fileName": "fileName",
      "documentNumber": "documentNumber",
      "documentDateTime": "documentDate",
      "irbmUniqueIdentifierNumber": "uuid",
      "fieldName": "documentLevelMisMatches.fieldLevelMisMatchList.fieldName",
      "valueInFile": "documentLevelMisMatches.fieldLevelMisMatchList.valueInFile",
      "valueAtLhdn": "documentLevelMisMatches.fieldLevelMisMatchList.valueAtLhdn"
    }
  },
  "csvHeaderMapping": {
    "ctFieldToHeaderMapping": {
      "fieldValueCt": "Value sent to ClearTax",
      "fieldValueLhdn": "Value reported to LHDN"
    }
  }
}
```

---

## 9. End-to-end summary diagram

```
BFF summary:  Frontend → PublicDocumentReadController(/viewSummary)
                       → TemplateFactory → EinvoiceTransactionalReconReadService.viewSummary
                       → EinvoiceQueryBuilder (purchase counts + mismatch count aggregations)
                       → EInvoiceDocumentService / EInvoiceTransactionalReconService
                       → tenantReactiveMongoTemplate.aggregate(...)  → KPIs

BFF list:     ... → EinvoiceTransactionalReconReadService.viewList
                  → getDocumentDetailed → flatten mismatches → DTO rows

CSV export:   Frontend → /internal/data/v1/generate (EINVOICE_MY_TRANSACTIONAL_RECON)
                       → SQS → Worker → HarvesterCoreImpl
                       → MyTransactionalReconDataSource.extractDataStream/transformDataStream
                       → MyReconDataSourceHelper.addFieldToRecord (Avro rows)
                       → WriterStrategy (CSV) → S3 → pre-signed URL
```

---

## 10. Talking points for interviews

- The recon feature normalizes nested (document + line level) mismatches into flat, exportable
  rows for both UI and CSV.
- Two independent code paths (BFF read vs. async report), each with its own reactive Mongo
  repository and its own tenant/mode template qualifier.
- `$match`-based mismatch detection with `elemMatch` on nested arrays; `allowDiskUse(true)` for
  large aggregations.
- Config-driven columns, filter mappings, and CSV headers — no hardcoded schema.
- Access control enforced in-query via node type (TIN vs BRANCH_L2 → taxpayerOrgId/branchOrgId).
