How live sets are computed
==========================

A *live row* is a row that a correct reader of that format returns for the
chosen snapshot or version. Syncsafe builds the multiset of ``--keys`` values
for those rows on each side and compares the two bags.

Iceberg
-------

The table is opened with ``HadoopTables`` from the table directory. The current
snapshot is used unless ``--source-snapshot`` / ``--target-snapshot`` is set.

Physical counts come from ``DataFile.recordCount`` on the planned scan tasks.
Live rows come from ``IcebergGenerics``, which applies Iceberg v2 position
deletes and equality deletes. The difference is reported as
``deleted_row_count``.

Delta Lake
----------

The table is opened with Delta Kernel (``Table.forPath``) from the table
directory. The latest version is used unless ``--source-version`` /
``--target-version`` is set.

Each add-file is read in full, without predicate pushdown, so file-local row
indexes match the deletion-vector bitmap. On-disk deletion vectors (storage
types understood by Kernel, including path-based vectors written by the demo
fixtures) are applied row by row. ``physical_row_count`` is the number of
Parquet rows; ``live_row_count`` excludes positions present in the vector.

Why the two counts both matter
------------------------------

Delta UniForm 4.3 documents that Iceberg ``DataFile.recordCount`` on a UniForm
table that also has deletion vectors is the *physical* (pre-vector) count, not
the logical count. A tool that compares those metadata figures, or that compares
them to a Delta ``numRecords`` statistic, can report a match while a reader of
the Iceberg side still returns deleted rows — or the opposite, if it assumes the
number is already live.

Syncsafe always applies deletes and then compares the live keys. When the
physical counts are equal and the live counts are not, the report includes a
hint that the target may be missing delete files or deletion vectors.
