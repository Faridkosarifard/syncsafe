Report format
=============

``syncsafe verify`` writes a JSON object with ``schema_version`` ``1.0``.
``syncsafe report`` reads that file and prints it as text or as pretty JSON.

Top-level fields
----------------

``schema_version``
    Always ``1.0`` for this release.

``tool`` / ``tool_version``
    ``syncsafe`` and the release that produced the report, for example ``0.1.0``.

``generated_at``
    UTC timestamp in ISO-8601 form.

``key_columns``
    The columns used to identify a logical row, in the order given to ``--keys``.

``source`` / ``target``
    A side object (see below) for each table.

``verdict``
    ``aligned``, ``diverged`` or ``incomplete``.

``summary``
    Counts of ``resurrected``, ``missing`` and ``count_mismatch`` keys. These are
    totals, not the size of the sample list.

``sample_limit``
    Maximum number of diverging keys included in ``divergences``.

``divergences``
    A sample of disagreeing keys. Each entry has ``kind``, a rendered ``key``,
    ``key_values``, ``source_count`` and ``target_count``.

``hint``
    Optional prose. When the two sides have the same physical row count but
    different live counts, Syncsafe notes that the target may be missing delete
    files or deletion vectors.

Side object
-----------

``format``
    ``iceberg`` or ``delta``.

``location``
    File URI of the table directory.

``snapshot_id``
    Iceberg snapshot used for the read, or ``null``.

``version``
    Delta table version used for the read, or ``null``.

``key_columns``
    Repeated from the top level for that side.

``live_row_count``
    Rows remaining after deletes and deletion vectors are applied.

``physical_row_count``
    Sum of file-level record counts (pre-delete). This is the same kind of
    figure UniForm records on Iceberg ``DataFile.recordCount`` when deletion
    vectors are present.

``deleted_row_count``
    ``physical_row_count - live_row_count`` (floored at zero).

``error``
    Present when the side could not be read completely. The verdict is then
    ``incomplete``.

Exit codes
----------

* ``0`` — ``aligned``
* ``2`` — usage error (unknown flag, missing ``--keys``, and so on)
* ``3`` — ``diverged``
* ``4`` — ``incomplete``
