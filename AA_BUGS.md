# Echo Android Auto — bug log

| # | Area | Repro | Observed | Expected | Status |
|---|------|-------|----------|----------|--------|
| 1 | Home grid | Open Home tab | Category tiles showed broken-image "!" triangle | Fallback Echo icon | fixed (verify) |
| 2 | Tabs | — | English labels, Home opened first | Hebrew labels, Library first | fixed (verify) |
| 3 | Search | Search → tap an **artist** result | Shows a "weird menu" (artist tile routed to USER page → no handler → fell back to tabs) | Open artist's page | fixed (verify) |
| 4 | Voice | "Gemini/Hey Google, play X on Echo" | (wanted) | Resolve query → play via Combine | already implemented (onAddMediaItems); test |
