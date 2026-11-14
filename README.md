# idea-difftastic

An IntelliJ Platform plugin that brings [difftastic](https://github.com/Wilfred/difftastic)'s
syntax-aware diff into the IDE's own diff viewer — useful for reviewing changes:
whitespace churn, formatting-only rewraps and moved delimiters no longer produce
noisy diffs, and the exact changed tokens are highlighted inside each changed line.

## How it works

The plugin registers a **Difftastic** entry in the diff window's viewer chooser
(the same dropdown as "Side-by-side" / "Unified"). It works everywhere two-side
diffs are shown: git diff, code review, local history, "Compare with …", shelf, etc.

When selected, it runs the external `difft` executable
(`difft --display json`, gated by difftastic's `DFT_UNSTABLE` env var), converts the
resulting structural line alignment into the platform's own diff fragments, and
layers difftastic's novel-atom highlights on top. Gutter, folding, change
navigation, aligned mode and all diff actions keep working. Any failure (missing
binary, timeout, unparsable output) falls back to the built-in comparison with a
notification.

## Requirements

- `difft` >= 0.60 on your machine:

  ```bash
  brew install difftastic   # or: cargo install difftastic
  ```

  A GUI-launched IDE on macOS does not inherit your shell `PATH`, so the plugin
  also probes `/opt/homebrew/bin`, `/usr/local/bin`, `~/.cargo/bin` and `~/bin`.
  The executable can also be configured explicitly under
  **Settings | Tools | Diff & Merge | Difftastic**.

## Settings

**Settings | Tools | Diff & Merge | Difftastic**

- Enable/disable the viewer
- `difft` executable path (auto-detected when empty)
- Extra arguments passed to `difft` (e.g. `--byte-limit 500000`)
- Timeout and max file size guards
- "Set Difftastic as the default viewer for two-side diffs"

## Development

```bash
./gradlew test        # unit tests (no difft needed)
./gradlew buildPlugin # build the plugin zip
./gradlew runIde      # sandbox IDE for manual testing
```

## License

MIT. difftastic itself is MIT — see its repository for details.
