import sys
sys.path.insert(1, "../../")
from h2o import enterprise as e
from h2o.exceptions import H2OValueError

# The H2O-3 Enterprise paywall (marketing demo) blocks getting a model *out* of
# OSS: MOJO download/export (model.download_mojo / model.save_mojo) and POJO
# download (model.download_pojo). MOJO import/upload are intentionally left
# unblocked. No cluster needed: block is the first statement of the blocked entry
# points, so it raises before touching a model or the backend. (The real
# enforcement is server-side; this covers the client-facing message.)


def enterprise_block_raises():
    for op in ("MOJO export", "POJO download"):
        try:
            e.block(op)
            assert False, "expected block(%r) to raise" % op
        except H2OValueError as ex:
            msg = str(ex)
            assert op in msg, msg
            assert "requires H2O-3 Enterprise" in msg, msg
            assert "enterprise@h2o.ai" in msg, msg
    print("OK enterprise_block_raises")


if __name__ == "__main__":
    enterprise_block_raises()
    print("\nALL ENTERPRISE MOJO BLOCK TESTS PASSED")
else:
    enterprise_block_raises()
