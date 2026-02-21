# Issue #1 Cherry-pick 清单

> Issue: `feat(sui-move): close positional fields semantic loop`
>
> 目标：仅合入 Issue #1（tuple/positional fields 全链路）相关改动，排除 README/docs 等无关文件。

## 1) 必选（核心语义闭环）

### 语法与 PSI
- `src/main/grammars/MoveParser.bnf`
- `src/main/kotlin/org/sui/lang/core/psi/ext/MvFieldName.kt` (new)
- `src/main/kotlin/org/sui/lang/core/psi/ext/MvTupleFieldDecl.kt` (new)
- `src/main/kotlin/org/sui/lang/core/psi/ext/MvFieldsOwner.kt`
- `src/main/kotlin/org/sui/lang/core/psi/ext/MvStruct.kt`
- `src/main/kotlin/org/sui/lang/core/psi/ext/MvStructField.kt`
- `src/main/kotlin/org/sui/lang/core/psi/ext/MvStructLitField.kt`
- `src/main/kotlin/org/sui/lang/core/psi/ext/MvFieldPatFull.kt`
- `src/main/kotlin/org/sui/lang/core/psi/ext/MvStructDotField.kt`
- `src/main/kotlin/org/sui/lang/core/psi/ext/MvPath.kt`
- `src/main/kotlin/org/sui/lang/core/psi/ext/MvBindingPat.kt`

### 解析与类型推断
- `src/main/kotlin/org/sui/lang/core/resolve2/NameResolution2.kt`
- `src/main/kotlin/org/sui/lang/core/resolve2/ref/MvPath2ReferenceImpl.kt`
- `src/main/kotlin/org/sui/lang/core/types/infer/TypeInferenceWalker.kt`
- `src/main/kotlin/org/sui/lang/core/types/infer/Patterns.kt`
- `src/main/kotlin/org/sui/lang/core/types/infer/TypeError.kt`

## 2) 建议一并合入（IDE 展示/检查联动）

- `src/main/kotlin/org/sui/ide/inspections/MvUnresolvedReferenceInspection.kt`
- `src/main/kotlin/org/sui/ide/inspections/MvAbilityCheckInspection.kt`
- `src/main/kotlin/org/sui/ide/inspections/PhantomTypeParameterInspection.kt`
- `src/main/kotlin/org/sui/ide/hints/StructLiteralFieldsInfoHandler.kt`
- `src/main/kotlin/org/sui/ide/navigation/goto/MvNamedElementsVisitor.kt`
- `src/main/kotlin/org/sui/ide/structureView/MvStructureViewTreeElement.kt`

## 3) 测试与测试数据（建议全部）

### 单测
- `src/test/kotlin/org/sui/lang/resolve/ResolveStructFieldsTest.kt`
- `src/test/kotlin/org/sui/lang/types/ExpressionTypesTest.kt`
- `src/test/kotlin/org/sui/ide/inspections/MvTypeCheckInspectionTest.kt`
- `src/test/kotlin/org/sui/ide/inspections/MvUnresolvedReferenceInspectionTest.kt`
- `src/test/kotlin/org/sui/lang/parser/CompleteParsingTest.kt`

### parser fixture
- `src/test/resources/org/sui/lang/parser/complete/tuple_struct_fields.move` (new)
- `src/test/resources/org/sui/lang/parser/complete/tuple_struct_fields.txt` (new)

### parser 基线自动更新（由 OVERWRITE_TESTDATA 导致）
- `src/test/resources/org/sui/lang/parser/complete/match.txt`
- `src/test/resources/org/sui/lang/parser/partial/dot_exprs.txt`
- `src/test/resources/org/sui/lang/parser/partial/expressions.txt`

> 说明：这 3 个文件建议带上，否则 parser 相关测试在目标分支可能因基线不一致失败。

## 4) 明确排除（非 Issue #1）

- `AGENTS.md`
- `README.md`
- `README.zh_CN.md`
- `docs/` 下其它本次协作文档/脚本（除本清单外）

## 5) 本地打包为单提交（便于 cherry-pick）

在当前分支执行（按需删减）：

```bash
git add \
  src/main/grammars/MoveParser.bnf \
  src/main/kotlin/org/sui/lang/core/psi/ext/MvFieldName.kt \
  src/main/kotlin/org/sui/lang/core/psi/ext/MvTupleFieldDecl.kt \
  src/main/kotlin/org/sui/lang/core/psi/ext/MvFieldsOwner.kt \
  src/main/kotlin/org/sui/lang/core/psi/ext/MvStruct.kt \
  src/main/kotlin/org/sui/lang/core/psi/ext/MvStructField.kt \
  src/main/kotlin/org/sui/lang/core/psi/ext/MvStructLitField.kt \
  src/main/kotlin/org/sui/lang/core/psi/ext/MvFieldPatFull.kt \
  src/main/kotlin/org/sui/lang/core/psi/ext/MvStructDotField.kt \
  src/main/kotlin/org/sui/lang/core/psi/ext/MvPath.kt \
  src/main/kotlin/org/sui/lang/core/psi/ext/MvBindingPat.kt \
  src/main/kotlin/org/sui/lang/core/resolve2/NameResolution2.kt \
  src/main/kotlin/org/sui/lang/core/resolve2/ref/MvPath2ReferenceImpl.kt \
  src/main/kotlin/org/sui/lang/core/types/infer/TypeInferenceWalker.kt \
  src/main/kotlin/org/sui/lang/core/types/infer/Patterns.kt \
  src/main/kotlin/org/sui/lang/core/types/infer/TypeError.kt \
  src/main/kotlin/org/sui/ide/inspections/MvUnresolvedReferenceInspection.kt \
  src/main/kotlin/org/sui/ide/inspections/MvAbilityCheckInspection.kt \
  src/main/kotlin/org/sui/ide/inspections/PhantomTypeParameterInspection.kt \
  src/main/kotlin/org/sui/ide/hints/StructLiteralFieldsInfoHandler.kt \
  src/main/kotlin/org/sui/ide/navigation/goto/MvNamedElementsVisitor.kt \
  src/main/kotlin/org/sui/ide/structureView/MvStructureViewTreeElement.kt \
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

# 可选：
# git commit -m "feat(sui-move): close positional fields semantic loop"
# git cherry-pick <commit_sha>
```

## 6) 验证命令

```bash
./gradlew test --no-daemon
```

