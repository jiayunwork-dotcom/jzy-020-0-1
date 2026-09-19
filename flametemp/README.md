# flametemp — 定压绝热火焰温度衡算服务

燃烧实验室用：给定燃料、当量比、进气温度，解出定压、绝热条件下的产物摩尔分数
与绝热火焰温度。**仅经 HTTP 提供求解与取回**，无前端、无账户；一个请求就是一次
衡算迭代，落成一条作业，按编号取回残差曲线与组成。不提供把多个无关单点工况
打成一包的入口。

## 物理模型（钉死项）

- 基准：1 mol 燃料；空气 O2/N2 = 0.21/0.79（按摩尔）。
- 燃料：`CH4`（甲烷）、`C2H6`（乙烷）。
- 当量比 `phi` 必须为正有限数；`phi = 1` 为化学计量。
- 产物集合钉死为 **CO2、H2O、O2、N2**（富燃时另带未燃燃料作稀释），
  **不做 CO / H2 离解**：
  - 贫燃/计量：燃料完全燃烧，过量 O2 直通；
  - 富燃：只有 `1/phi` 的燃料可被氧氧化，剩余 `1 - 1/phi` 燃料直通
    （“过量燃料稀释”），C/H/O/N 仍闭合。
- 元素闭合按 **C、H、O、N 原子** 核对，不按总摩尔数（烃燃烧总摩尔数可变）。
  N2 不氧化，产物 N2 摩尔数 = 进气 N2。
- 焓：`H(T) = ΔHf(298.15) + ∫Cp dT`，由 NASA-7 温度多项式给出，
  **反应物与产物共用同一套系数**（GRI-Mech 3.0，随 Cantera `gri30.yaml`
  发布；系数内置于 `ThermoTable`，无第二套表）。
- 定压、忽略动能。绝热平衡：`H_products(T) = H_reactants(Tin)`。

### 钉死的数值判据（作业结果里原样回显）

| 量 | 值 |
|---|---|
| Cp 多项式求值温域 | 200 K – 3500 K，界外求值作业失败 |
| 焓闭合容差 | `|Hp − Hr| ≤ 10 J / mol 燃料` |
| 原子残差阈值 | 每个元素 `< 1e-9 mol 原子 / mol 燃料` |
| 迭代方式 | 符号二分；初始猜测为温域中点 |
| 收敛序列 | 只收录使残差严格下降的点；一旦进容差立即停止，不向另一个根乱跳 |
| 最大步数 | 50 个收录点 / 200 次求值；耗尽仍不闭合则作业失败（不编温度） |
| 甲烷化学计量示范区间 | 2200 K – 2400 K（本实现 298.15 K 进气下 ≈ 2325 K） |

## 模块划分（求解器不堆在一个文件里）

```
thermo/     焓与 Cp 温度多项式：Species, NasaSegment, NasaThermo, ThermoTable（唯一系数源）, 越界异常
balance/    元素衡算：FuelDefinition/FuelRegistry, Mixture, AtomBalance, Stoichiometry
solver/     焓衡算、温度迭代与收敛判定：EnthalpyBalance, FlameTemperatureSolver,
            FlameTempService（编排+入参检查）, SolveOutcome/IterationPoint/SolverConstants
job/        作业存取与入参落库：JobService, JobRepository（进程内 SQLite）, JobRecord, DemoJobSeeder
web/        HTTP：JobController, 类型化错误（ApiExceptionHandler/BadRequestException）
```

## 构建与启动（一条命令成容器并启动）

```bash
./scripts/up.sh
# 本机 JDK 17 镜像标签不同时：
# BUILD_IMAGE=本机maven-jdk17镜像 RUNTIME_IMAGE=本机jre17镜像 ./scripts/up.sh
```

镜像分两阶段：`maven:3.9-eclipse-temurin-17` 构建、`eclipse-temurin:17-jre`
运行；SQLite 是镜像内的进程内文件库（`/data/flametemp.db`，docker 卷
`flametemp-data`），**不起数据库容器**。服务监听 8080。

不用容器时（本机有 JDK 17）：

```bash
mvn -DskipTests package
FLAMETEMP_DB=./flametemp.db java -jar target/flametemp-1.0.0.jar
```

启动时若库为空，会内置 1 号示范作业：甲烷、phi=1、298.15 K。

## HTTP

### 提交一次衡算 `POST /api/jobs`

```json
{"fuel": "CH4", "equivalenceRatio": 1.0, "inletTemperatureK": 298.15}
```
→ `200 {"id": 2, "status": "ACCEPTED"}`

### 按编号取回全文 `GET /api/jobs/{id}`

返回：输入参数、状态、最终温度、**逐迭代温度与焓残差**、收敛判据与容差、
C/H/O/N 原子残差与阈值、反应物/产物摩尔数与摩尔分数、失败原因（如有）。

`GET /api/jobs` 只给作业摘要列表。

### 类型化错误（带 `type`）

| 情况 | 状态码 | type |
|---|---|---|
| 未知燃料 | 422 | `UNKNOWN_FUEL` |
| 当量比 ≤ 0 或非有限 | 422 | `INVALID_EQUIVALENCE_RATIO` |
| 进气温度 ≤ 0 或非有限 | 422 | `INVALID_INLET_TEMPERATURE` |
| 缺字段 / 字段类型错 / JSON 坏 | 400 | `MISSING_FIELD` / `INVALID_FIELD` / `MALFORMED_REQUEST` |
| 编号不存在 | 404 | `JOB_NOT_FOUND` |

求解类失败（如进气过热导致焓在温域内不闭合）返回 200 建作业，作业状态
`FAILED`、`finalTemperatureK` 为 `null`、`failure.reason = ROOT_NOT_BRACKETED`
等，并同样可按编号取回。

## 回归用例

`mvn test`（共 32 个），与题目对错点一一对应：

- 甲烷化学计量温度落在 2200–2400 K 钉死区间，且示范作业末步残差 < 容差；
- 贫燃（0.8）与富燃（1.2）均低于化学计量；
- 进气 298.15→400 K：火焰温度上升，升幅（≈73 K）小于进气升幅（101.85 K）；
- C/H/O/N 原子残差 < 1e-9；乙烷化学计量（≈2379 K）≠ 甲烷（≈2325 K）；
- 焓不闭合（Tin=3500 K）作业失败、无最终温度；
- 未知燃料、非正当量比/进气温度在迭代前被拒；
- 16 条作业 8 线程并行提交，各自残差序列单调且互不串扰；
- 另含 NASA 表对表校核（298.15 K 生成焓、1000/2000 K 显焓、Cp 连续性、越界即抛错）。
