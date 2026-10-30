#!/usr/bin/env python3
"""Run filesystem-only GTIT stamp tests after the parent explicitly releases the runtime gate."""
import argparse
import hashlib
from pathlib import Path
import subprocess
import os

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--source-root', type=Path, default=Path(__file__).resolve().parents[2])
p.add_argument('--classpath-file', type=Path, required=True)
p.add_argument('--java-home', type=Path, required=True)
p.add_argument('--work-dir', type=Path, required=True)
a = p.parse_args()
a.work_dir.mkdir(parents=True, exist_ok=True)
cp = a.classpath_file.read_text().strip()
suffix = 'java/com/miaokatze/gtit/crossmod/bq/'
sources = [a.source_root / ('src/main/' + suffix + 'BqStampFile.java'),
           a.source_root / ('src/test/' + suffix + 'BqStampFileTest.java')]
(a.work_dir / 'source-sha256.txt').write_text('\n'.join(hashlib.sha256(x.read_bytes()).hexdigest() + ' ' + str(x.resolve()) for x in sources) + '\n')
(a.work_dir / 'classpath-sha256.txt').write_text('\n'.join(hashlib.sha256(Path(x).read_bytes()).hexdigest() + ' ' + x for x in cp.split(os.pathsep) if Path(x).is_file()) + '\n')
compile_result = subprocess.run([str(a.java_home / 'bin/javac'), '-encoding', 'UTF-8', '-cp', cp,
                                 '-d', str(a.work_dir)] + [str(s) for s in sources], capture_output=True, text=True)
(a.work_dir / 'compile.log').write_text(compile_result.stdout + compile_result.stderr)
if compile_result.returncode:
    print(compile_result.stdout + compile_result.stderr)
    raise SystemExit(compile_result.returncode)
result = subprocess.run([str(a.java_home / 'bin/java'), '-Djava.io.tmpdir=' + str(a.work_dir.resolve()), '-cp', os.pathsep.join((str(a.work_dir), cp)),
                         'com.miaokatze.gtit.crossmod.bq.BqStampFileTest'], capture_output=True, text=True)
(a.work_dir / 'tests.log').write_text(result.stdout + result.stderr)
print(result.stdout + result.stderr)
raise SystemExit(result.returncode)
