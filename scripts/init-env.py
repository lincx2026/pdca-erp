"""Create local secrets without overwriting an existing .env or printing secrets."""
from pathlib import Path
import os
import secrets

root = Path(__file__).resolve().parents[1]
target = root / ".env"
template = (root / ".env.example").read_text(encoding="utf-8")
for key, length in (("POSTGRES_PASSWORD", 32), ("REDIS_PASSWORD", 32),
                    ("JWT_SECRET", 48), ("BOOTSTRAP_ADMIN_PASSWORD", 24)):
    template = "\n".join(
        f"{key}={secrets.token_hex(length)}" if line.startswith(key + "=") else line
        for line in template.splitlines()
    ) + "\n"
fd = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as stream:
    stream.write(template)
print("Created .env; configure OSS and read BOOTSTRAP_ADMIN_PASSWORD locally.")
