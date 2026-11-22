Contributing to Syncsafe
========================

Thanks for considering a contribution. Syncsafe compares live row sets across
table formats, so correctness of delete application matters more than features.

Getting started
---------------

.. code-block:: bash

   git clone https://github.com/Faridkosarifard/syncsafe
   cd syncsafe
   make check      # Spotless, Checkstyle, tests and coverage gates

Requirements: JDK 17 or newer. The Maven Wrapper (``./mvnw``) downloads Maven
itself. No Spark cluster or Docker image is required; tests and ``syncsafe demo``
write fixture tables on the local filesystem.

Workflow
--------

1. Open an issue first for anything larger than a small fix, so the approach can
   be agreed on.
2. Create a branch from ``main``.
3. Add or update tests. A new delete kind (for example Iceberg v3 deletion
   vectors or a Hudi reader) needs a unit test and an end-to-end test that
   builds a real table.
4. Run ``make check`` and make sure it passes.
5. Update ``CHANGELOG.rst`` under *Unreleased*.
6. Open a pull request describing the behaviour you changed.

Rules for live-set readers
--------------------------

* Never treat ``DataFile.recordCount`` or Delta ``numRecords`` as the live
  count. Those figures are physical (pre-delete). Apply position deletes,
  equality deletes and deletion vectors, and report both numbers.
* A read that cannot finish must surface an error on that side so the verdict
  becomes ``incomplete``. A silent empty live set would hide a failed conversion
  check.
* Destructive operations are out of scope: Syncsafe does not rewrite, convert or
  delete data files.

Style
-----

* Google Java Format via Spotless (``make format``) and the Checkstyle rules in
  ``checkstyle.xml``.
* Comments explain *why* a count is physical or live, not only *what* the code
  does.
* Commit messages: imperative mood, a short summary line, and a body that
  explains the reason for the change.
* Code, comments, names and documentation are English only.

License
-------

By contributing you agree that your contributions are licensed under the Apache
License 2.0.
