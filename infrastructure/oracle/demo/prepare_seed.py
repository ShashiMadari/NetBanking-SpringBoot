"""Generate private local demo credentials and SQL; never include them in images."""
import json
import secrets
import sys
from pathlib import Path

import bcrypt


def main():
    config_path, template_path, output_path = map(Path, sys.argv[1:])
    config = json.loads(config_path.read_text(encoding="utf-8-sig"))
    users = config["users"]
    emails = [u["email"].strip().lower() for u in users]
    if len(set(emails)) != 3 or any("@" not in e for e in emails):
        raise ValueError("Supply three different valid email addresses.")
    for user, email in zip(users, emails):
        user["email"] = email
        if any(c in user["name"] + email + user["phone"] for c in "\r\n\x00"):
            raise ValueError("Names, emails and phones must be single-line values.")
        if not 1 <= len(user["name"]) <= 120 or len(email) > 120:
            raise ValueError("Name/email exceeds database limits.")
        if not user["phone"].isdigit() or not 10 <= len(user["phone"]) <= 15:
            raise ValueError("Phone must have 10 to 15 digits.")
    if len({u["phone"] for u in users}) != 3:
        raise ValueError("Use three different phone numbers.")

    text = template_path.read_text(encoding="utf-8")
    replacements = {}
    details = ["O-Bank PRIVATE local demo login details", "Application: http://localhost:8001", ""]
    for i, (prefix, user) in enumerate(zip(("CUSTOMER1", "CUSTOMER2", "ADMIN"), users)):
        for field in ("name", "email", "phone"):
            replacements[f"__{prefix}_{field.upper()}__"] = user[field].replace("'", "''")
        password = "Ob!9" + secrets.token_urlsafe(12)
        replacements[f"PASSWORD_HASH_{i}"] = bcrypt.hashpw(password.encode(), bcrypt.gensalt(rounds=10)).decode()
        details.extend([user["name"], user["email"], f"Role: {'ADMIN' if i == 2 else 'CUSTOMER'}", f"Password: {password}", ""])
    pin = str(1000 + secrets.randbelow(9000))
    replacements["CARD_PIN_HASH"] = bcrypt.hashpw(pin.encode(), bcrypt.gensalt(rounds=10)).decode()
    # Replace once in one pass: replacement values cannot introduce template tokens.
    import re
    pattern = re.compile("|".join(re.escape(k) for k in sorted(replacements, key=len, reverse=True)))
    text = pattern.sub(lambda m: replacements[m.group()], text)
    details.extend([f"Demo debit/credit card PIN for both customers: {pin}", "Use Sign in; these users are already activated. Login requires an email OTP.", "Select administrator sign-in for the admin.", "Do not upload this file or the generated SQL to GitHub/Quay."])
    output_path.mkdir(parents=True, exist_ok=False)
    (output_path / "seed.sql").write_text(text, encoding="utf-8")
    (output_path / "login-details.txt").write_text("\n".join(details) + "\n", encoding="utf-8")
    print("Prepared local seed SQL and private login details.")


if __name__ == "__main__":
    main()
