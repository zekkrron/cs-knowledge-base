1. current CTC : 11 LPA, expected CTC : 18-30 LPA (but mentioning this range is not correct for every company this is only for my range, for a company the range I wold say will be within this range with a range of 5 LPA depending on what the company actually pays)

2. okay so I also needed to update the resume there as there have been updates to it. I have given the new one in new-resume.tex which is to be deleted later. not only that but now we need to up the number of requests per day to 3 million. also then 

3. remove the orion pointer to the resume and instead of that we will add another pointer :

\resumeItem{\textbf{Self-Hosted Voice AI Agent:} Built an in-house driver-calling voice agent (Dograh/Pipecat, Gemini 3.5 Flash, Sarvam STT/TTS, Vobiz SIP) that captures live location, delay reason and ETA for non-GPS trips and auto-applies NSL and ETA in MTS through a LangGraph post-call workflow. Scaled it to \textbf{8 load-balanced workers}, cut reply latency by \textbf{$\sim$1s}, and brought cost down to \textbf{₹2.5 per call}, \textbf{$\sim$47\% lower} than the earlier Hunar voice agent.}

or 

\resumeItem{\textbf{Voice AI Agent:} Shipped a self-hosted driver-calling agent (Pipecat, Gemini, Sarvam, Vobiz) with an automated LangGraph NSL/ETA workflow, now serving live routes at \textbf{₹2.5/call} (\textbf{$\sim$47\% lower} than Hunar).}

whichever fits the size


and dograh-voice-agent... md file is made in the workspace root and you will have to take it and put it in the correct folder in resume and then use this in resume tex file as well

and this after updated in resume and md file deleted from the workspace base and put in correct place then go ahead with updating your own parts of the job update regarding these

and resume pdfs I don't think we should keep in this repo but tex files we will keep 2 for agentic heavy roles and SDE backend heavy roles but for now keep one only

so as of now do these things rest we will do later