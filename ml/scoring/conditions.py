import ast
import operator
from collections.abc import Callable, Mapping
from typing import Any

_COMPARISONS: dict[type[ast.cmpop], Callable[[Any, Any], bool]] = {
    ast.Lt: operator.lt,
    ast.LtE: operator.le,
    ast.Gt: operator.gt,
    ast.GtE: operator.ge,
    ast.Eq: operator.eq,
    ast.NotEq: operator.ne,
}


class ConditionError(ValueError):
    pass


def _dotted_name(node: ast.expr) -> str:
    if isinstance(node, ast.Name):
        return node.id
    if isinstance(node, ast.Attribute):
        return f"{_dotted_name(node.value)}.{node.attr}"
    raise ConditionError(f"недопустимое имя: {ast.unparse(node)}")


def _is_negative_number(node: ast.expr) -> bool:
    return (
        isinstance(node, ast.UnaryOp)
        and isinstance(node.op, ast.USub)
        and isinstance(node.operand, ast.Constant)
        and type(node.operand.value) in (int, float)
    )


class Condition:
    def __init__(self, source: str) -> None:
        self.source = source
        try:
            self._tree = ast.parse(source, mode="eval").body
        except SyntaxError as exc:
            raise ConditionError(f"не разобрать условие «{source}»: {exc.msg}") from exc
        self.names = frozenset(self._collect_names(self._tree))

    def _collect_names(self, node: ast.expr) -> list[str]:
        if isinstance(node, (ast.Name, ast.Attribute)):
            return [_dotted_name(node)]
        if isinstance(node, ast.BoolOp):
            return [name for value in node.values for name in self._collect_names(value)]
        if isinstance(node, ast.UnaryOp) and isinstance(node.op, ast.Not):
            return self._collect_names(node.operand)
        if _is_negative_number(node):
            return []
        if isinstance(node, ast.Compare):
            if not all(type(op) in _COMPARISONS for op in node.ops):
                raise ConditionError(f"недопустимое сравнение в «{self.source}»")
            return [name for part in (node.left, *node.comparators) for name in self._collect_names(part)]
        if isinstance(node, ast.Constant) and isinstance(node.value, (int, float, str, bool)):
            return []
        raise ConditionError(f"недопустимое выражение в «{self.source}»: {ast.unparse(node)}")

    def evaluate(self, context: Mapping[str, Any]) -> bool:
        return bool(self._eval(self._tree, context))

    def _eval(self, node: ast.expr, context: Mapping[str, Any]) -> Any:
        if isinstance(node, (ast.Name, ast.Attribute)):
            name = _dotted_name(node)
            if name not in context:
                raise ConditionError(f"неизвестное имя «{name}» в «{self.source}»")
            return context[name]
        if isinstance(node, ast.Constant):
            return node.value
        if isinstance(node, ast.BoolOp):
            values = (self._eval(value, context) for value in node.values)
            return all(values) if isinstance(node.op, ast.And) else any(values)
        if _is_negative_number(node):
            return -node.operand.value
        if isinstance(node, ast.UnaryOp) and isinstance(node.op, ast.Not):
            return not self._eval(node.operand, context)
        if isinstance(node, ast.Compare):
            left = self._eval(node.left, context)
            for op, comparator in zip(node.ops, node.comparators, strict=True):
                right = self._eval(comparator, context)
                if not _COMPARISONS[type(op)](left, right):
                    return False
                left = right
            return True
        raise ConditionError(f"недопустимое выражение в «{self.source}»")
