# P2-E Controller Read-Endpoint Authorization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `@PreAuthorize` to all 30 unguarded read (GET) endpoints across 12 controllers, with matrix-determined view authorities, proven by RBAC 403 integration tests.

**Architecture:** Pure additive method-level `@PreAuthorize` annotations (all controllers already import it). Authorities come verbatim from `RBAC角色权限矩阵.md`. New RBAC 403 tests reuse the existing `RbacIntegrationTest` `@WithMockUser` pattern. No business logic, no write endpoints, no intentionally-open endpoints touched.

**Tech Stack:** Spring Security (`@PreAuthorize`), Spring Boot Test (`@WithMockUser`, MockMvc), JUnit 5. Matches the P1-A fix (`7d932aa`).

---

## Conventions (apply in every task)

- **Insertion point:** for each named method, insert the `@PreAuthorize(...)` line **immediately before the `public` method signature line**, after the method's existing `@Operation`/`@Parameter` annotations (matching the codebase convention `@*Mapping → @Operation → @PreAuthorize → method`). Locate methods by their unique name.
- **Annotation form:** `@PreAuthorize("hasRole('ADMIN') or hasAuthority('<perm>')")`.
- All 12 controllers already import `org.springframework.security.access.prepost.PreAuthorize` — **no import changes needed**.
- **RED→GREEN per task:** write the new RBAC 403 test(s) first, run to confirm they FAIL (status != 403, because endpoints are currently unguarded), then add the annotations, run to confirm PASS (403). Commit only when green.
- RBAC tests go in `src/test/java/com/insurance/aml/integration/RbacIntegrationTest.java` and use the file's existing FQN style for `get`: `org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(...)`.

---

### Task 1: High-severity — screening + monitoring (Screening, Graph, Rule, Transaction)

**Files:**
- Modify: `src/main/java/com/insurance/aml/module/screening/controller/ScreeningController.java`
- Modify: `src/main/java/com/insurance/aml/module/monitoring/controller/GraphController.java`
- Modify: `src/main/java/com/insurance/aml/module/monitoring/controller/RuleController.java`
- Modify: `src/main/java/com/insurance/aml/module/monitoring/controller/TransactionController.java`
- Modify: `src/test/java/com/insurance/aml/integration/RbacIntegrationTest.java`

- [ ] **Step 1: Add the 2 failing RBAC tests**

Append these two methods to `RbacIntegrationTest` (before the final `customerJson` helper / closing brace):

```java
    @Test
    @WithMockUser(username = "viewer", authorities = {"ROLE_VIEWER", "customer:view"})
    @DisplayName("无 screening:view 用户读取筛查结果 -> 403")
    void viewerCannotReadScreeningResults() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/screening/results").param("customerId", "1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @WithMockUser(username = "viewer", authorities = {"ROLE_VIEWER", "customer:view"})
    @DisplayName("无 monitoring:view 用户读取交易列表 -> 403")
    void viewerCannotReadTransactions() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/monitoring/transactions/page"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }
```

- [ ] **Step 2: Run to confirm RED**

Run: `mvn -q test -Dtest=RbacIntegrationTest`
Expected: the 2 new tests FAIL (status is 200, not 403 — endpoints currently unguarded). If a new test errors with 400 (missing param) instead of running the handler, adjust the request to supply required params so the handler is reached; the point is it must not already return 403.

- [ ] **Step 3: Add annotations — ScreeningController**

Insert `@PreAuthorize("hasRole('ADMIN') or hasAuthority('screening:view')")` before each of these methods: `getResults`, `getWhitelist`.

- [ ] **Step 4: Add annotations — GraphController**

Insert `@PreAuthorize("hasRole('ADMIN') or hasAuthority('monitoring:view')")` before each of: `detectRingTransactions`, `traceMultiLayerTransfer`, `detectSharedAccounts`, `analyzeNetworkDensity`.

- [ ] **Step 5: Add annotations — RuleController**

Insert `@PreAuthorize("hasRole('ADMIN') or hasAuthority('monitoring:view')")` before each of: `pageQueryRules`, `getRuleVersions`.

- [ ] **Step 6: Add annotations — TransactionController**

Insert `@PreAuthorize("hasRole('ADMIN') or hasAuthority('monitoring:view')")` before each of: `pageQueryTransactions`, `getDailySummary`.

- [ ] **Step 7: Run to confirm GREEN**

Run: `mvn -q test -Dtest=RbacIntegrationTest`
Read `target/surefire-reports/com.insurance.aml.integration.RbacIntegrationTest.txt`. Expected: all tests pass (the 8 pre-existing P1-A/baseline RBAC tests + 2 new = 10), `Failures: 0, Errors: 0`.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/insurance/aml/module/screening/controller/ScreeningController.java \
        src/main/java/com/insurance/aml/module/monitoring/controller/GraphController.java \
        src/main/java/com/insurance/aml/module/monitoring/controller/RuleController.java \
        src/main/java/com/insurance/aml/module/monitoring/controller/TransactionController.java \
        src/test/java/com/insurance/aml/integration/RbacIntegrationTest.java
git commit -m "fix: enforce view authority on screening + monitoring read endpoints"
```

---

### Task 2: High-severity — assessment + reporting + product (SelfAssessment, Rectification, StrReport, Reporting, Product)

**Files:**
- Modify: `src/main/java/com/insurance/aml/module/assessment/controller/SelfAssessmentController.java`
- Modify: `src/main/java/com/insurance/aml/module/assessment/controller/RectificationController.java`
- Modify: `src/main/java/com/insurance/aml/module/casemgmt/controller/StrReportController.java`
- Modify: `src/main/java/com/insurance/aml/module/reporting/controller/ReportingController.java`
- Modify: `src/main/java/com/insurance/aml/module/product/controller/ProductController.java`
- Modify: `src/test/java/com/insurance/aml/integration/RbacIntegrationTest.java`

- [ ] **Step 1: Add the 4 failing RBAC tests**

Append to `RbacIntegrationTest`:

```java
    @Test
    @WithMockUser(username = "investigator", authorities = {"ROLE_INVESTIGATOR", "customer:view"})
    @DisplayName("无 assessment:view 用户读取自评估列表 -> 403")
    void investigatorCannotReadAssessments() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/assessments/list"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @WithMockUser(username = "investigator", authorities = {"ROLE_INVESTIGATOR", "customer:view"})
    @DisplayName("无 report:str 用户读取STR报告列表 -> 403")
    void investigatorCannotReadStrReports() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/str-reports/page"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @WithMockUser(username = "investigator", authorities = {"ROLE_INVESTIGATOR", "customer:view"})
    @DisplayName("无 report:view 用户读取大额报送列表 -> 403")
    void investigatorCannotReadLargeTxnReports() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/reporting/large-txn/page"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @WithMockUser(username = "investigator", authorities = {"ROLE_INVESTIGATOR", "customer:view"})
    @DisplayName("无 product:view 用户读取产品列表 -> 403")
    void investigatorCannotReadProducts() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/products/page"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }
```

- [ ] **Step 2: Run to confirm RED**

Run: `mvn -q test -Dtest=RbacIntegrationTest`
Expected: the 4 new tests FAIL (status 200, not 403). Same param caveat as Task 1.

- [ ] **Step 3: Add annotations — SelfAssessmentController**

Insert `@PreAuthorize("hasRole('ADMIN') or hasAuthority('assessment:view')")` before each of: `getAssessmentDetail`, `listAssessments`.

- [ ] **Step 4: Add annotations — RectificationController**

Insert `@PreAuthorize("hasRole('ADMIN') or hasAuthority('assessment:view')")` before: `listTasks`.

- [ ] **Step 5: Add annotations — StrReportController**

Insert `@PreAuthorize("hasRole('ADMIN') or hasAuthority('report:str')")` before each of: `pageQuery`, `getReportDetail`.

- [ ] **Step 6: Add annotations — ReportingController**

Insert `@PreAuthorize("hasRole('ADMIN') or hasAuthority('report:view')")` before each of: `pageQueryReports`, `previewXml`.

- [ ] **Step 7: Add annotations — ProductController**

Insert `@PreAuthorize("hasRole('ADMIN') or hasAuthority('product:view')")` before each of: `getProductDetail`, `pageQueryProducts`, `getAssessmentHistory`.

- [ ] **Step 8: Run to confirm GREEN**

Run: `mvn -q test -Dtest=RbacIntegrationTest`
Expected: all tests pass (10 from Task 1 + 4 new = 14), `Failures: 0, Errors: 0`.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/insurance/aml/module/assessment/controller/SelfAssessmentController.java \
        src/main/java/com/insurance/aml/module/assessment/controller/RectificationController.java \
        src/main/java/com/insurance/aml/module/casemgmt/controller/StrReportController.java \
        src/main/java/com/insurance/aml/module/reporting/controller/ReportingController.java \
        src/main/java/com/insurance/aml/module/product/controller/ProductController.java \
        src/test/java/com/insurance/aml/integration/RbacIntegrationTest.java
git commit -m "fix: enforce view authority on assessment/report/product read endpoints"
```

---

### Task 3: Low-severity — Customer, Alert, Case (consistency / defense-in-depth)

These modules' view permission is held by all 4 roles, so there is no role to assert a 403 against; correctness is verified by compilation + no regression. Pure additive annotations.

**Files:**
- Modify: `src/main/java/com/insurance/aml/module/kyc/controller/CustomerController.java`
- Modify: `src/main/java/com/insurance/aml/module/alert/controller/AlertController.java`
- Modify: `src/main/java/com/insurance/aml/module/casemgmt/controller/CaseController.java`

- [ ] **Step 1: Add annotations — CustomerController**

Insert `@PreAuthorize("hasRole('ADMIN') or hasAuthority('customer:view')")` before each of: `getCustomerDetail`, `pageQueryCustomers`, `getCustomer360View`, `getCustomerRelationshipGraph`.

- [ ] **Step 2: Add annotations — AlertController**

Insert `@PreAuthorize("hasRole('ADMIN') or hasAuthority('alert:view')")` before each of: `pageQueryAlerts`, `getAlertDetail`, `getDispositionChain`, `getAlertStatistics`.

- [ ] **Step 3: Add annotations — CaseController**

Insert `@PreAuthorize("hasRole('ADMIN') or hasAuthority('case:view')")` before each of: `getCaseDetail`, `pageQueryCases`.

- [ ] **Step 4: Compile + targeted regression**

Run: `mvn -q test-compile`
Expected: exit 0.
Run: `mvn -q test -Dtest='RbacIntegrationTest,CustomerIntegrationTest,CustomerServiceImplTest,AlertServiceImplTest,CaseServiceImplTest'`
Expected: all pass — confirms the new annotations don't break the existing customer/alert/case flows (these tests authenticate as admin or use authorities that include the relevant view permission).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/insurance/aml/module/kyc/controller/CustomerController.java \
        src/main/java/com/insurance/aml/module/alert/controller/AlertController.java \
        src/main/java/com/insurance/aml/module/casemgmt/controller/CaseController.java
git commit -m "chore: add view authority to customer/alert/case read endpoints (defense-in-depth)"
```

---

### Task 4: Full regression + finalize

- [ ] **Step 1: Full suite**

Run: `mvn -q test > /dev/null 2>&1; echo "exit=$?"`
Expected: `exit=0`.

- [ ] **Step 2: Aggregate count (confirm +6 RBAC tests, no regression)**

Run: `grep -h 'Tests run' target/surefire-reports/*.txt | awk -F'[, ]+' '{run+=$3; fail+=$5; err+=$7} END {print "run="run" fail="fail" err="err}'`
Expected: `run=275 fail=0 err=0` (269 baseline + 6 new RBAC tests).

- [ ] **Step 3: Verify no unguarded read remains (re-run the audit parser)**

Run the method-block parser from the audit (comment-stripped, per-method); confirm the 12 controllers now show all read endpoints guarded except the intentionally-open set (Auth/Health/Dashboard/Notification/Dict). Spot-check: `grep -c '@PreAuthorize' src/main/java/com/insurance/aml/module/kyc/controller/CustomerController.java` should now be 7 (3 pre-existing writes + 4 added reads).

- [ ] **Step 4: Merge to main and push** (matches established session flow)

```bash
git checkout main
git merge --ff-only fix/p2e-controller-read-authz
git branch -d fix/p2e-controller-read-authz
git push github main
```

- [ ] **Step 5: Report** spec §6 acceptance coverage + the GraphController/GraphAnalysisController duplication backlog note.

---

## Self-Review

**Spec coverage:**
- §3 high-severity 20 endpoints / 9 controllers → Tasks 1+2 (Screening, Graph, Rule, Transaction, SelfAssessment, Rectification, StrReport, Reporting, Product) ✓.
- §3 low-severity 10 endpoints / 3 controllers → Task 3 (Customer, Alert, Case) ✓.
- §3 authority mapping: every controller's annotation matches the spec table (screening:view, monitoring:view, assessment:view, report:str, report:view, product:view, customer:view, alert:view, case:view) ✓.
- §5 tests: 6 RBAC 403 tests (screening, monitoring, assessment, str-report, reporting, product) → Tasks 1+2 ✓.
- §6 acceptance 1–4 → Tasks 1–4 (annotations, 403 tests, no write/business change, full regression 275) ✓.
- §7 non-goals (GraphController dedup, Dict, write endpoints) → untouched ✓.

**Placeholder scan:** none. Every annotation line is fully specified; every test method is complete code. Method names are exact (from the source-accurate audit). The only per-method judgement is locating the method by name and inserting one line above its `public` signature — fully determined.

**Type consistency:** authority strings are identical to the spec table and to existing usages in the same controllers' write endpoints. Test endpoints (`/screening/results`, `/monitoring/transactions/page`, `/assessments/list`, `/str-reports/page`, `/reporting/large-txn/page`, `/products/page`) match the audited base paths; all bind cleanly (DTO/defaulted/optional params, verified). Denied roles chosen so they lack the gated permission (VIEWER lacks screening:view/monitoring:view; INVESTIGATOR lacks assessment:view/report:str/report:view/product:view per matrix).

No gaps found.
