<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Idea-difftastic Changelog

## [Unreleased]

### Added

- "Difftastic" entry in the diff window's viewer chooser: syntax-aware diffs
  computed by the external `difft` executable (difftastic >= 0.60), rendered in
  the stock two-side viewer with novel-atom highlighting.
- Graceful fallback to the built-in comparison (with a notification) when difft
  is missing, too old, times out or returns unparsable output.
- Settings page under Settings | Tools | Diff & Merge | Difftastic: executable
  path, extra arguments, timeout, max file size, and an option to make
  Difftastic the default two-side diff viewer.
