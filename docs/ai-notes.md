# AI Usage Notes (Storyteller Tech Lead Task)

## How I used AI (common Agile SDLC, human in the loop)
### Execution style
I acted like an engineering lead: I didn’t hand-type the code, but I reviewed and validated every change. Cursor/Claude did the typing; I used my eyes and voice to direct and approve the work. This is “engineering with AI,” not fire-and-forget coding.

### Steps
- **Briefing & requirements:** used Grok3 to simulate a customer meeting, read the brief, clarified unclear points, and collected current best practices.  
- **Architecture & planning:** ran a team-style grooming to pick architecture, tech stack, tests, environments, and how to meet scalability/SLA.  
- **Decomposition & setup:** broke work into project setup → infra → coding → end-to-end testing; iterated in short agile cycles.  
- **Implementation:** worked in Cursor IDE with Claude Sonnet 4.5; generated code/scripts/configs and built the features.  
- **Testing:** ran tests to match expectations; rebuilt from a clean repo/environment to ensure end-to-end correctness.  
- **Code review & polish:** self-reviewed files, dependencies, and decisions; fixed issues and optimized after green tests.  
- **Documentation:** wrote and edited docs to keep them readable and useful.  
- **Delivery:** finalized the repo and packaged the deliverable.


## What worked well
- Grok3: gave a detailed read of the brief, clarified expectations and “why”, and surfaced current industry best practices.  
- Cursor + Claude Sonnet 4.5: spun up infra largely on its own, offered solid troubleshooting hints, and generated code quickly.  
- Quality/throughput: codegen was fast and good enough to keep momentum; with human review on top, it stayed reliable.

## What didn’t
- Grok3’s proposed project structure was unusable (non-working files) and had to be discarded.  
- Started on Anthropic and immediately hit token limits, so I had to switch to Cursor/Claude to complete the work.  
- Some AI suggestions tried to bloat data models beyond the “keep it simple” requirement; I trimmed them back to stay within the brief.

