package com.miaokatze.gtit.testlab;

import java.io.File;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.GsonBuilder;
import com.miaokatze.gtit.Tags;
import com.miaokatze.gtit.main.GTInterestingThing;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;

final class TestLabReport {

    final int schema = 1;
    final String runId = UUID.randomUUID()
        .toString();
    final String mode;
    final String startedAt = Instant.now()
        .toString();
    String finishedAt;
    String status;
    final Map<String, Object> environment = new LinkedHashMap<>();
    final Map<String, Double> metrics = new LinkedHashMap<>();
    final List<Map<String, String>> cases = new ArrayList<>();
    private transient final long startNanos = System.nanoTime();

    TestLabReport(String mode) {
        this(mode, true);
    }

    TestLabReport(String mode, boolean captureEnvironment) {
        this.mode = mode;
        if (!captureEnvironment) return;
        environment.put("gtitVersion", Tags.VERSION);
        environment.put("javaVersion", System.getProperty("java.version"));
        environment.put("jvm", System.getProperty("java.vm.name"));
        environment.put("os", System.getProperty("os.name"));
        environment.put(
            "processors",
            Runtime.getRuntime()
                .availableProcessors());
        environment.put(
            "maxHeapBytes",
            Runtime.getRuntime()
                .maxMemory());
        environment.put("dimensionId", TestLabConfig.dimensionId);
        environment.put("visualAcceptance", "not performed; client acknowledgements only establish screen opening");
        Map<String, String> mods = new LinkedHashMap<>();
        for (ModContainer mod : Loader.instance()
            .getActiveModList()) mods.put(mod.getModId(), mod.getVersion());
        environment.put("mods", mods);
    }

    void add(String scenario, String name, String result, String detail) {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("scenario", scenario);
        entry.put("check", name);
        entry.put("status", result);
        entry.put("detail", detail);
        cases.add(entry);
    }

    void finish() {
        finishedAt = Instant.now()
            .toString();
        metrics.put("wallMillis", (System.nanoTime() - startNanos) / 1_000_000.0);
        long failed = cases.stream()
            .filter(entry -> "FAIL".equals(entry.get("status")))
            .count();
        long passed = cases.stream()
            .filter(entry -> "PASS".equals(entry.get("status")))
            .count();
        long skipped = cases.size() - failed - passed;
        metrics.put("passed", (double) passed);
        metrics.put("failed", (double) failed);
        metrics.put("skipped", (double) skipped);
        boolean incompleteCoverage = false;
        Object selected = environment.get("selectedScenarios");
        if (selected instanceof String[]ids) for (String id : ids) {
            boolean executed = cases.stream()
                .anyMatch(
                    entry -> id.equals(entry.get("scenario"))
                        && ("PASS".equals(entry.get("status")) || "FAIL".equals(entry.get("status"))));
            if (!executed) incompleteCoverage = true;
        }
        status = failed > 0 ? "FAIL" : passed > 0 && !incompleteCoverage ? "PASS" : "SKIP";
    }

    /** Called only by the enabled lab's report executor, after all server-thread mutations have finished. */
    void write() {
        File output = new File(TestLabConfig.reportDirectory, runId + ".json");
        try {
            Files.createDirectories(
                output.toPath()
                    .getParent());
            try (Writer writer = Files.newBufferedWriter(output.toPath(), StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting()
                    .disableHtmlEscaping()
                    .create()
                    .toJson(this, writer);
            }
            GTInterestingThing.LOG
                .info("[GTIT-LAB] COMPLETE run={} status={} report={}", runId, status, output.getAbsolutePath());
        } catch (Exception | LinkageError error) {
            GTInterestingThing.LOG.error("[GTIT-LAB] REPORT_FAILED run=" + runId, error);
        }
    }
}
