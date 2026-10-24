def is_valid_inn(inn: str) -> bool:
    if not inn.isdigit() or len(inn) not in (10, 12):
        return False
    digits = [int(c) for c in inn]
    if len(digits) == 10:
        weights = [2, 4, 10, 3, 5, 9, 4, 6, 8]
        check = sum(d * w for d, w in zip(digits[:9], weights)) % 11 % 10
        return check == digits[9]
    weights_11 = [7, 2, 4, 10, 3, 5, 9, 4, 6, 8]
    weights_12 = [3, 7, 2, 4, 10, 3, 5, 9, 4, 6, 8]
    check_11 = sum(d * w for d, w in zip(digits[:10], weights_11)) % 11 % 10
    check_12 = sum(d * w for d, w in zip(digits[:11], weights_12)) % 11 % 10
    return check_11 == digits[10] and check_12 == digits[11]
