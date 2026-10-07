#!/usr/bin/env python3
"""Phase 18 (Files): the zip fixtures (Fixtures paragraph; build task 12).

    make_zips.py <output dir> [--skip name[,name...]]

Writes, with python's zipfile on the host, every zip the rows and the JVM zip-guard tests use, and prints each
file's md5 (and the md5 of each entry of qa.zip, the values E13 compares an extract with):

  qa.zip          one.txt (1,024 bytes), dir/two.bin (307,200 bytes), ü-name.txt (UTF-8 flag set)
  qa-bad.zip      ok.txt plus entries named ../../evil.txt and /sdcard/abs.txt
  qa-corrupt.zip  the first 500 bytes of qa.zip
  qa-enc.zip      Info-ZIP's `zip -e -P qa` (/usr/bin/zip): general-purpose flag bit 0 set
  qa-bomb.zip     one 50 MB entry of zeros whose central-directory and local-header uncompressed sizes read 1,024
  qa-huge.zip     one 3 GB entry of zeros, about 3 MB compressed, declared honestly (zip64 sizes)
  qa-big.zip      200 MB of /dev/urandom, stored (progress and cancel)
  qa-cp437.zip    café.txt, its name in CP437 with the UTF-8 flag clear
  qa-nested.zip   holding qa.zip
  qa-zip64.zip    70,000 empty entries (a zip64 end record)
  qa-symlink.zip  ok.txt plus a Unix symlink entry `link` whose text is a private path (r3 D6 (d); E18)

Everything but qa-big.zip (urandom) and qa-enc.zip (zip's own salt) is byte-for-byte the same on every run.
--skip leaves named fixtures out (the JVM tests skip qa-big: 200 MB of nothing they assert on).
"""
import hashlib
import os
import random
import struct
import subprocess
import sys
import tempfile
import zipfile

WHEN = (2026, 1, 2, 3, 4, 6)
MB = 1024 * 1024
ZIP = "/usr/bin/zip"
SYMLINK_TEXT = b"/data/data/app.tileshell/files/secret"


def md5(path):
    h = hashlib.md5()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(MB), b""):
            h.update(block)
    return h.hexdigest()


def info(name, method=zipfile.ZIP_DEFLATED):
    z = zipfile.ZipInfo(name, date_time=WHEN)
    z.compress_type = method
    z.external_attr = 0o644 << 16
    return z


def qa_entries():
    """qa.zip's three entries, the same bytes on every run."""
    return [
        ("one.txt", (b"0123456789abcdef" * 64)),                 # 1,024 bytes
        ("dir/two.bin", random.Random(18).randbytes(307200)),     # 307,200 bytes, not compressible
        ("ü-name.txt", "ü is u-umlaut\n".encode("utf-8")),
    ]


def make_qa(out):
    with zipfile.ZipFile(out, "w") as z:
        for name, data in qa_entries():
            z.writestr(info(name), data)
    for name, data in qa_entries():
        print(f"{hashlib.md5(data).hexdigest()}  qa.zip:{name}")


def make_bad(out):
    with zipfile.ZipFile(out, "w") as z:
        z.writestr(info("ok.txt"), b"ok\n")
        z.writestr(info("../../evil.txt"), b"evil\n")
        z.writestr(info("/sdcard/abs.txt"), b"abs\n")
    names = zipfile.ZipFile(out).namelist()
    assert names == ["ok.txt", "../../evil.txt", "/sdcard/abs.txt"], names


def make_corrupt(out, qa):
    with open(qa, "rb") as f, open(out, "wb") as g:
        g.write(f.read(500))


def make_enc(out):
    with tempfile.TemporaryDirectory() as d:
        with open(os.path.join(d, "secret.txt"), "wb") as f:
            f.write(b"this entry is encrypted\n")
        if os.path.exists(out):
            os.remove(out)
        subprocess.run([ZIP, "-q", "-e", "-P", "qa", "-j", os.path.abspath(out), "secret.txt"], cwd=d, check=True)
    assert zipfile.ZipFile(out).infolist()[0].flag_bits & 1, "qa-enc.zip: the encrypted bit is not set"


def central_directory(data):
    """(offset of the first central-directory header, offset of the end record)."""
    end = data.rfind(b"PK\x05\x06")
    return struct.unpack_from("<I", data, end + 16)[0], end


def make_bomb(out):
    with zipfile.ZipFile(out, "w") as z:
        z.writestr(info("bomb.bin"), bytes(50 * MB))
    with open(out, "r+b") as f:
        data = f.read()
        cd, _ = central_directory(data)
        assert data[0:4] == b"PK\x03\x04" and data[cd:cd + 4] == b"PK\x01\x02"
        assert struct.unpack_from("<I", data, 22)[0] == 50 * MB and struct.unpack_from("<I", data, cd + 24)[0] == 50 * MB
        f.seek(22)                      # local header: uncompressed size
        f.write(struct.pack("<I", 1024))
        f.seek(cd + 24)                 # central directory: uncompressed size
        f.write(struct.pack("<I", 1024))


def make_huge(out):
    block = bytes(MB)
    with zipfile.ZipFile(out, "w") as z:
        with z.open(info("huge.bin"), "w", force_zip64=True) as f:
            for _ in range(3 * 1024):
                f.write(block)
    assert zipfile.ZipFile(out).infolist()[0].file_size == 3 * 1024 * MB


def make_big(out):
    with zipfile.ZipFile(out, "w") as z:
        with z.open(info("big.bin", zipfile.ZIP_STORED), "w") as f:
            for _ in range(200):
                f.write(os.urandom(MB))


class Cp437Info(zipfile.ZipInfo):
    """A name written in CP437 with the UTF-8 flag clear (zipfile itself would write UTF-8 and set the flag)."""

    def _encodeFilenameFlags(self):
        return self.filename.encode("cp437"), self.flag_bits


def make_cp437(out):
    z = Cp437Info("café.txt", date_time=WHEN)
    z.compress_type = zipfile.ZIP_DEFLATED
    z.external_attr = 0o644 << 16
    with zipfile.ZipFile(out, "w") as f:
        f.writestr(z, b"cafe\n")
    with open(out, "rb") as f:
        data = f.read()
    cd, _ = central_directory(data)
    assert data.count(b"caf\x82.txt") == 2, "qa-cp437.zip: the name is not CP437 in both headers"
    assert struct.unpack_from("<H", data, cd + 8)[0] & 0x800 == 0, "qa-cp437.zip: the UTF-8 flag is set"


def make_nested(out, qa):
    with open(qa, "rb") as f:
        inner = f.read()
    with zipfile.ZipFile(out, "w") as z:
        z.writestr(info("qa.zip", zipfile.ZIP_STORED), inner)
        z.writestr(info("readme.txt"), b"qa.zip is inside\n")


def make_zip64(out):
    with zipfile.ZipFile(out, "w") as z:
        for i in range(1, 70001):
            z.writestr(info(f"e{i:05d}.txt", zipfile.ZIP_STORED), b"")
    with open(out, "rb") as f:
        assert b"PK\x06\x06" in f.read(), "qa-zip64.zip: no zip64 end record"


def make_symlink(out):
    link = zipfile.ZipInfo("link", date_time=WHEN)
    link.create_system = 3                      # Unix
    link.external_attr = 0o120777 << 16         # S_IFLNK | 0777
    link.compress_type = zipfile.ZIP_STORED
    with zipfile.ZipFile(out, "w") as z:
        z.writestr(info("ok.txt"), b"ok\n")
        z.writestr(link, SYMLINK_TEXT)


def main(argv):
    if len(argv) < 2:
        sys.exit(__doc__)
    out_dir = argv[1]
    skip = set()
    if "--skip" in argv:
        skip = set(argv[argv.index("--skip") + 1].split(","))
    os.makedirs(out_dir, exist_ok=True)

    def path(name):
        return os.path.join(out_dir, name)

    qa = path("qa.zip")
    makers = [
        ("qa.zip", lambda p: make_qa(p)),
        ("qa-bad.zip", make_bad),
        ("qa-corrupt.zip", lambda p: make_corrupt(p, qa)),
        ("qa-enc.zip", make_enc),
        ("qa-bomb.zip", make_bomb),
        ("qa-huge.zip", make_huge),
        ("qa-big.zip", make_big),
        ("qa-cp437.zip", make_cp437),
        ("qa-nested.zip", lambda p: make_nested(p, qa)),
        ("qa-zip64.zip", make_zip64),
        ("qa-symlink.zip", make_symlink),
    ]
    for name, make in makers:
        if name in skip or name[:-4] in skip:
            continue
        make(path(name))
        print(f"{md5(path(name))}  {name}")


if __name__ == "__main__":
    main(sys.argv)
