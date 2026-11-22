Changelog
=========

All notable changes to this project are documented here. The format follows
`Keep a Changelog <https://keepachangelog.com/en/1.1.0/>`_ and the project uses
`Semantic Versioning <https://semver.org/>`_. The JSON report has its own
``schema_version``.

Unreleased
----------

0.1.0 - 2026-11-22
------------------

Added
~~~~~

* Live-set readers for Apache Iceberg (HadoopTables, format v2 position and
  equality deletes via IcebergGenerics) and Delta Lake (Delta Kernel 4.4, on-disk
  deletion vectors).
* Comparison of live key multisets with ``resurrected``, ``missing`` and
  ``count_mismatch`` divergences, plus a hint when physical counts match but
  live counts do not.
* ``syncsafe verify``, ``report`` and ``demo`` commands. JSON schema 1.0 and a
  human-readable text report. Exit codes 0 (aligned), 3 (diverged) and 4
  (incomplete).
* Local fixture tables that reproduce an aligned conversion and a conversion
  that copied data-file pointers but dropped deletion vectors.
* Unit and end-to-end tests, Spotless/Checkstyle, JaCoCo coverage gates and
  GitHub Actions CI.
