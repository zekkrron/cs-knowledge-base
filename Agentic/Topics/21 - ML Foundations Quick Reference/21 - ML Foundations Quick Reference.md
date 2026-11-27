---
tags: [agentic, status/draft]
created: 2026-11-27
---
# ML Foundations Quick Reference

> [!abstract] What this is
> The classic machine-learning and statistics questions that big-tech MLE and applied-scientist loops still ask next to LLM questions (Google "Explain ML model fundamentals", Amazon "Explain ML statistics and model design concepts", Microsoft "Explain transformers and deploy an LLM safely", 2025–26). One answer per question, interview depth. Not a full ML course: classic recommender, ads and fraud system design are out of scope for these notes ([[Syllabus and Coverage]]).

---

## Why It Exists

- Andrew Ng's skills map lists ML foundations (supervised learning, bias and variance, error analysis) as core even for engineers who build on LLMs (DeepLearning.AI, Sep 2026).
- The reported Google and Amazon screens ask a rapid list: logistic regression assumptions, Naive Bayes, multi-class metrics, bagging vs boosting, overfitting, RNN vs LSTM, attention, CNNs, autoencoders, hypothesis tests, the law of large numbers, Poisson vs exponential, causal inference basics.

---

## Generalisation

| Concept | One-line answer |
|---|---|
| **Bias vs variance** | Bias: error from a model too simple to fit the pattern (underfits; train and validation error both high). Variance: error from fitting noise (overfits; train low, validation high). More data cuts variance, not bias |
| **Overfitting fixes** | More data or augmentation, regularisation (L2 shrinks weights, L1 drives some to zero), dropout, early stopping on validation loss, simpler model, ensembling |
| **Train / validation / test** | Fit on train, choose hyperparameters on validation, report once on test. Time-ordered data splits by time |
| **Leakage** | Information from the future or the label in features (a "refund issued" flag predicting fraud); results look great offline and fail online |
| **Error analysis** | Read a sample of mistakes, group them by cause, fix the biggest group first; the same loop drives LLM evals ([[11 - Evals Observability and Silent Drift]]) |

---

## Classic Models

| Model | What to say |
|---|---|
| **Logistic regression** | Linear model of the log-odds, trained by maximising likelihood (log loss). Assumes independent observations, a linear relation between features and log-odds, little multicollinearity (unstable coefficients, not worse predictions); outputs well-calibrated probabilities when well specified |
| **Naive Bayes** | Bayes' rule assuming features independent given the class; counts give the probabilities. Works well with many sparse features and little data (text, spam), because the ranking of classes survives the wrong independence assumption even when the probabilities don't |
| **Decision tree** | Recursive splits that most reduce impurity (Gini, entropy) or variance; high variance alone |
| **Bagging / random forest** | Many deep trees on bootstrap samples (and random feature subsets), averaged: **reduces variance**. More trees never overfit, they just stop helping. Correlated features split importance between them but barely hurt accuracy |
| **Boosting / XGBoost** | Shallow trees added one at a time, each fitting the previous errors (gradients): **reduces bias**. Too many trees overfit, so use learning rate, depth limits and early stopping. Multicollinearity mostly affects importance scores (one of the correlated features gets picked), not accuracy |
| **k-means, PCA** | k-means: assign to nearest centroid, move centroids, repeat; pick k by elbow or silhouette. PCA: directions of maximum variance (eigenvectors of the covariance); for compression and visualisation |

---

## Deep Learning Families

| Family | What to say |
|---|---|
| **CNN** | Convolutions share small filters across positions (translation equivariance), pooling or strides shrink resolution, deeper layers see larger regions; images, audio spectrograms |
| **RNN** | Processes a sequence step by step with a hidden state; gradients vanish or explode over long sequences and steps can't be parallelised |
| **LSTM / GRU** | Gates (input, forget, output) and a cell state that carries information with additive updates, so gradients survive longer; still sequential |
| **Transformer** | Self-attention lets every token look at every other in one step: parallel training and direct long-range connections, at quadratic cost in sequence length ([[00 - LLM Fundamentals for Engineers#Inside the transformer (engineer depth)]]) |
| **Attention** | `softmax(QKᵀ ÷ √d) V`: each query scores all keys, the weights mix the values; the √d keeps scores from saturating the softmax |
| **Autoencoder** | Encoder compresses to a small code, decoder reconstructs; used for denoising, anomaly detection (high reconstruction error), compression and learned embeddings; variational autoencoders make the code space sample-able |

---

## Metrics

| Situation | Metric | Why |
|---|---|---|
| Binary, balanced | Accuracy, ROC-AUC | Threshold-free ranking quality |
| Binary, rare positives (fraud, abuse) | **Precision, recall, PR-AUC**, recall at a fixed precision | ROC-AUC looks good even when positives are buried |
| Multi-class | **Macro-F1** (every class equal; use when small classes matter), **micro-F1** / accuracy (every example equal), per-class confusion matrix, top-k accuracy | Choose by whether rare classes matter |
| Probabilities feed a decision | **Log loss, calibration** (reliability curve, expected calibration error, Brier score) | A score of 0.8 should be right 80% of the time; recalibrate with Platt scaling or isotonic regression |
| Ranking and retrieval | NDCG, MRR, recall@k | Position matters ([[08 - Vector DB Internals and Hybrid Search]]) |

- **Class imbalance:** stratified splits; class weights or resampling; pick the threshold on the business cost of false positives vs false negatives, not 0.5 ([[11 - LLM Content Moderation Pipeline]]).

---

## Probability and Statistics

| Question | One-line answer |
|---|---|
| **Law of large numbers** | The sample mean converges to the true mean as the sample grows |
| **Central limit theorem** | The sample mean is approximately normal with standard error `σ ÷ √n`, whatever the underlying distribution (finite variance); the basis of confidence intervals ([[11 - Evals Observability and Silent Drift#Eval Statistics: Error Bars on Every Score]]) |
| **Poisson vs exponential** | Poisson counts events in a fixed interval at rate λ; the exponential is the waiting time between those events (mean 1/λ), and it's memoryless |
| **Hypothesis test steps** | State H₀ and H₁; choose α and the test; compute the statistic and p-value; reject H₀ if p < α; report the effect size and interval, not only significance |
| **Statistical difference vs distance** | Significance says a difference is unlikely to be noise; a distance (KL divergence, Wasserstein, total variation) measures how far apart two distributions are. A tiny distance can be significant with enough data |
| **Moment generating function** | `M(t) = E[e^{tX}]`; its derivatives at 0 give the moments, and MGFs of independent sums multiply, which makes sums of random variables easy to analyse |
| **p-hacking and multiple comparisons** | Many tests produce false positives; correct with Bonferroni or Benjamini–Hochberg |

---

## Causal Inference Basics

- **Correlation isn't causation** because of confounders (a variable driving both treatment and outcome).
- **Randomised experiments** (A/B tests) remove confounding by design ([[11 - Evals Observability and Silent Drift#Product Metrics and the Launch Decision]]).
- **Without randomisation:** difference-in-differences (compare changes before and after against a control group), regression discontinuity (units just either side of a threshold), instrumental variables, propensity-score matching or weighting; each rests on an assumption you must state.

---

## Interview Lines

> [!tip] Say it like this
> - "Bagging averages many high-variance trees to cut variance; boosting adds shallow trees on the residuals to cut bias, so boosting can overfit with too many trees and a forest can't."
> - "For rare positives I report PR-AUC and recall at a fixed precision; ROC-AUC flatters a model that buries positives."
> - "Macro-F1 when small classes matter, micro-F1 when every example counts the same, and always the per-class confusion matrix."
> - "Calibration matters whenever a score feeds a decision; I check a reliability curve and fix it with isotonic regression."
> - "Significance isn't size: with enough data a negligible difference is significant, so I report the effect and its interval."

## Sources

- PracHub, *Explain ML model fundamentals* (Google, MLE onsite) and *Explain ML statistics and model design concepts* (Amazon, applied scientist screen), 2025–26. https://prachub.com/interview-questions/explain-ml-model-fundamentals · https://prachub.com/interview-questions/explain-ml-statistics-and-model-design-concepts
- DeepLearning.AI, *AI Engineering Skills Map* (Sep 2026): machine-learning foundations as a core skill. https://www.deeplearning.ai/resources/ai-engineering-skills
- T. Hastie, R. Tibshirani, J. Friedman, *The Elements of Statistical Learning* (2nd ed., 2009): bagging, boosting, bias-variance.
- C. Guo et al., *On Calibration of Modern Neural Networks* (ICML 2017).

## Related Notes

- [[00 - LLM Fundamentals for Engineers]] · [[11 - Evals Observability and Silent Drift]] · [[15 - Post-Training and Model Adaptation]] · [[11 - LLM Content Moderation Pipeline]]
- [[Agentic/README]]
