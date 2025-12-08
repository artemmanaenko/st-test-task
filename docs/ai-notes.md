# AI Usage Notes

## How I used AI

### Methodology
Common iterative Agile, single-person team covering all roles

### Execution style
I acted as the orchestrator: defined requirements, checked work, and made decisions; the AI played multiple roles (coder, doc drafter, suggester). Code and docs look auto-generated because the AI did the typing under my direction, but the output is the result of my decomposition and review, not a single prompt or fire-and-forget generation.

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
- Cursor IDE + Claude Sonnet 4.5: spun up infra largely on its own, offered solid troubleshooting hints, and generated code quickly.  
- Quality/throughput: codegen was fast and good enough to keep momentum; with human review on top, it stayed reliable.

## What didn’t
- Grok3’s proposed project structure was unusable (non-working files) and had to be discarded.  
- Started on Antigravity IDE and immediately hit token limits, so I had to switch to Cursor IDE/Claude to complete the work.  
- Some AI suggestions tried to bloat data models beyond the “keep it simple” requirement; I trimmed them back to stay within the brief.

