"""Independent frozen oracle for docs/reading-anchors.md; Python stdlib only.

Run: python reading-core/src/test/resources/anchors/generate_v1.py --check
Without --check, rewrites v1-framing.json beside this script.
Input strings are stored as UTF-16 units, so JSON decoder surrogate policy cannot
silently replace isolated units before Kotlin tests see them.
"""
import hashlib
import json
from pathlib import Path
import struct
import sys


def u32(value):
    if not 0 <= value <= 0x7fffffff:
        raise ValueError("nonnegative JVM Int required")
    return struct.pack(">I", value)


def encode_block(tag, text=""):
    if tag == 1:
        return bytes([tag]), []
    raw = text.encode("utf-16-be", errors="surrogatepass")
    units = list(struct.unpack(">" + "H" * (len(raw) // 2), raw))
    return bytes([tag]) + u32(len(units)) + raw, units


def fixture(label, blocks):
    framed = [encode_block(*block) for block in blocks]
    document = b"grimoire-reading-document\0" + u32(1) + u32(len(blocks)) + b"".join(b for b, _ in framed)
    return {"label": label,
            "blocks": [{"tag": tag, "units": units} for (tag, *_), (_, units) in zip(blocks, framed)],
            "documentFrameHex": document.hex(),
            "logicalRevision": hashlib.sha256(document).hexdigest(),
            "blockFingerprints": [hashlib.sha256(b"grimoire-reading-block\0" + u32(1) + b).hexdigest() for b, _ in framed]}


cases = [
    fixture("empty-document", []),
    fixture("empty-text", [(0, "")]),
    fixture("separator", [(1,)]),
    fixture("empty-image", [(2, "")]),
    fixture("text-x", [(0, "x")]),
    fixture("image-x", [(2, "x")]),
    fixture("ab-c", [(0, "ab"), (0, "c")]),
    fixture("a-bc", [(0, "a"), (0, "bc")]),
    fixture("repeated-block", [(0, "same"), (0, "same")]),
    fixture("isolated-high", [(0, "\ud800")]),
    fixture("isolated-low", [(0, "\udc00")]),
    fixture("replacement-character", [(0, "\ufffd")]),
    fixture("question-mark", [(0, "?")]),
    fixture("unicode-controls", [(0, "\x00\x1c\x1d\x1e\x1f\u4e2d\U0001f600e\u0301\ud800x\udc00\uffff"),
                                 (1,), (2, "https://example.org/\u4e2d\x00")]),
]
chunked = fixture("chunk-boundary", [(0, "a" * 2047 + "\U0001f600" + "z" * 4097)])
payload = "{\n  \"cases\": [\n" + ",\n".join("    " + json.dumps(case, separators=(",", ":")) for case in cases)
payload += "\n  ],\n  \"chunkBoundary\":" + json.dumps({k: chunked[k] for k in ["logicalRevision", "blockFingerprints"]}) + "\n}\n"
destination = Path(__file__).with_name("v1-framing.json")
if "--check" in sys.argv:
    if destination.read_text(encoding="utf-8") != payload:
        raise SystemExit("Frozen framing fixture does not match the independent oracle")
    print("14 framing vectors and chunk-boundary digests match")
else:
    destination.write_text(payload, encoding="utf-8")
