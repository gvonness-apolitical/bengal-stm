# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.9.x] - 2024

### Changed
- Updated sbt-typelevel to 0.8.1
- Requires Java 21

## [0.8.x] - 2023

### Changed
- Moved to Typelevel SBT plugin
- Updated Scalafmt to Scala 3 dialect
- Code cleanup and improvements

## [0.7.x] - 2022

### Added
- Initial public release
- Software Transactional Memory implementation for Cats Effect
- `TxnVar` for transactional variables
- `TxnVarMap` for transactional maps
- Intelligent runtime scheduler with static analysis
- Semantic blocking via `waitFor`
