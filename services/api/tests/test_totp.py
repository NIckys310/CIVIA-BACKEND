import pyotp

from civia_api.security import totp


def test_valid_code_is_accepted_and_returns_step() -> None:
    secret = totp.new_secret()
    now = 1_800_000_000.0
    code = pyotp.TOTP(secret).at(now)
    assert totp.verify(secret, code, last_step=None, now=now) == totp.current_step(now)


def test_previous_step_tolerated_for_clock_drift() -> None:
    secret = totp.new_secret()
    now = 1_800_000_000.0
    old = pyotp.TOTP(secret).at(now - 30)
    assert totp.verify(secret, old, last_step=None, now=now) is not None


def test_code_cannot_be_reused() -> None:
    secret = totp.new_secret()
    now = 1_800_000_000.0
    code = pyotp.TOTP(secret).at(now)
    step = totp.verify(secret, code, last_step=None, now=now)
    assert totp.verify(secret, code, last_step=step, now=now + 5) is None


def test_garbage_is_rejected() -> None:
    secret = totp.new_secret()
    for code in ("", "12345", "abcdef", "1234567"):
        assert totp.verify(secret, code, last_step=None) is None


def test_recovery_codes_are_unique_and_hash_normalized() -> None:
    codes = totp.new_recovery_codes()
    assert len(set(codes)) == 10
    assert all(len(c) == 11 and c[5] == "-" for c in codes)
    assert totp.hash_recovery_code(codes[0].lower()) == totp.hash_recovery_code(codes[0])
