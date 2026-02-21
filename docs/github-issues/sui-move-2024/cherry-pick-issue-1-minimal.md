# Issue #1 最小合入集（核心 + 必测）

> Issue: `feat(sui-move): close positional fields semantic loop`
>
> 策略：仅保留“功能闭环必须代码 + 验收必测”。不包含结构视图/导航/能力检查等可选联动。

## 1) 核心代码（最小闭环）

- `src/main/grammars/MoveParser.bnf`
- `src/main/kotlin/org/sui/lang/core/psi/ext/MvFieldName.kt` (new)
- `src/main/kotlin/org/sui/lang/core/psi/ext/MvTupleFieldDecl.kt` (new)
- `src/main/kotlin/org/sui/lang/core/psi/ext/MvFieldsOwner.kt`
- `src/main/kotlin/org/sui/lang/core/psi/ext/MvPath.kt`
- `src/main/kotlin/org/sui/lang/core/psi/ext/MvBindingPat.kt`
- `src/main/kotlin/org/sui/lang/core/psi/ext/MvStructField.kt`
- `src/main/kotlin/org/sui/lang/core/psi/ext/MvStructLitField.kt`
- `src/main/kotlin/org/sui/lang/core/psi/ext/MvFieldPatFull.kt`
- `src/main/kotlin/org/sui/lang/core/psi/ext/MvStructDotField.kt`
- `src/main/kotlin/org/sui/lang/core/resolve2/NameResolution2.kt`
- `src/main/kotlin/org/sui/lang/core/resolve2/ref/MvPath2ReferenceImpl.kt`
- `src/main/kotlin/org/sui/lang/core/types/infer/Patterns.kt`
- `src/main/kotlin/org/sui/lang/core/types/infer/TypeError.kt`
- `src/main/kotlin/org/sui/lang/core/types/infer/TypeInferenceWalker.kt`
- `src/main/kotlin/org/sui/ide/inspections/MvUnresolvedReferenceInspection.kt`

## 2) 必测（Issue #1 验收）

### 单测
- `src/test/kotlin/org/sui/lang/resolve/ResolveStructFieldsTest.kt`
- `src/test/kotlin/org/sui/lang/types/ExpressionTypesTest.kt`
- `src/test/kotlin/org/sui/ide/inspections/MvTypeCheckInspectionTest.kt`
- `src/test/kotlin/org/sui/ide/inspections/MvUnresolvedReferenceInspectionTest.kt`
- `src/test/kotlin/org/sui/lang/parser/CompleteParsingTest.kt`

### parser fixture（新增）
- `src/test/resources/org/sui/lang/parser/complete/tuple_struct_fields.move` (new)
- `src/test/resources/org/sui/lang/parser/complete/tuple_struct_fields.txt` (new)

### parser 基线（必须带）
- `src/test/resources/org/sui/lang/parser/complete/match.txt`
- `src/test/resources/org/sui/lang/parser/partial/dot_exprs.txt`
- `src/test/resources/org/sui/lang/parser/partial/expressions.txt`

> 原因：这 3 个文件包含本次语法变化导致的既有解析树/错误文案更新；不带会导致 parser 回归失败。

## 3) 明确不在最小集（可后续单独合入）

- `src/main/kotlin/org/sui/ide/hints/StructLiteralFieldsInfoHandler.kt`
- `src/main/kotlin/org/sui/ide/inspections/MvAbilityCheckInspection.kt`
- `src/main/kotlin/org/sui/ide/inspections/PhantomTypeParameterInspection.kt`
- `src/main/kotlin/org/sui/ide/navigation/goto/MvNamedElementsVisitor.kt`
- `src/main/kotlin/org/sui/ide/structureView/MvStructureViewTreeElement.kt`
- `src/main/kotlin/org/sui/lang/core/psi/ext/MvStruct.kt`

## 4) 一次性 `git add`（最小集）

```bash
git add \
  src/main/grammars/MoveParser.bnf \
  src/main/kotlin/org/sui/lang/core/psi/ext/MvFieldName.kt \
  src/main/kotlin/org/sui/lang/core/psi/ext/MvTupleFieldDecl.kt \
  src/main/kotlin/org/sui/lang/core/psi/ext/MvFieldsOwner.kt \
  src/main/kotlin/org/sui/lang/core/psi/ext/MvPath.kt \
  src/main/kotlin/org/sui/lang/core/psi/ext/MvBindingPat.kt \
  src/main/kotlin/org/sui/lang/core/psi/ext/MvStructField.kt \
  src/main/kotlin/org/sui/lang/core/psi/ext/MvStructLitField.kt \
  src/main/kotlin/org/sui/lang/core/psi/ext/MvFieldPatFull.kt \
  src/main/kotlin/org/sui/lang/core/psi/ext/MvStructDotField.kt \
  src/main/kotlin/org/sui/lang/core/resolve2/NameResolution2.kt \
  src/main/kotlin/org/sui/lang/core/resolve2/ref/MvPath2ReferenceImpl.kt \
  src/main/kotlin/org/sui/lang/core/types/infer/Patterns.kt \
  src/main/kotlin/org/sui/lang/core/types/infer/TypeError.kt \
  src/main/kotlin/org/sui/lang/core/types/infer/TypeInferenceWalker.kt \
  src/main/kotlin/org/sui/ide/inspections/MvUnresolvedReferenceInspection.kt \
  src/test/kotlin/org/sui/lang/resolve/ResolveStructFieldsTest.kt \
  src/test/kotlin/org/sui/lang/types/ExpressionTypesTest.kt \
  src/test/kotlin/org/sui/ide/inspections/MvTypeCheckInspectionTest.kt \
  src/test/kotlin/org/sui/ide/inspections/MvUnresolvedReferenceInspectionTest.kt \
  src/test/kotlin/org/sui/lang/parser/CompleteParsingTest.kt \
  src/test/resources/org/sui/lang/parser/complete/tuple_struct_fields.move \
  src/test/resources/org/sui/lang/parser/complete/tuple_struct_fields.txt \
  src/test/resources/org/sui/lang/parser/complete/match.txt \
  src/test/resources/org/sui/lang/parser/partial/dot_exprs.txt \
  src/test/resources/org/sui/lang/parser/partial/expressions.txt
```

## 5) 最小集验证

```bash
./gradlew test --no-daemon
```

