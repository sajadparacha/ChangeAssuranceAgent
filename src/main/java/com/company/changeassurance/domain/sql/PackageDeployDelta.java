package com.company.changeassurance.domain.sql;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Spec vs body package touches discovered from a deploy SQL script.
 * Oracle catalog / script analysis only — application source is not scanned.
 */
public record PackageDeployDelta(
        boolean specChanged,
        boolean bodyChanged,
        String primaryPackageName,
        String primarySchemaOwner,
        List<String> packageNames,
        boolean inferredFromScript,
        Map<String, PackageTouch> touchesByPackage
) {
    public PackageDeployDelta {
        packageNames = List.copyOf(packageNames == null ? List.of() : packageNames);
        touchesByPackage = Map.copyOf(touchesByPackage == null ? Map.of() : touchesByPackage);
        if (primaryPackageName != null && !primaryPackageName.isBlank()) {
            primaryPackageName = primaryPackageName.trim().toUpperCase(Locale.ROOT);
        } else {
            primaryPackageName = null;
        }
        if (primarySchemaOwner != null && !primarySchemaOwner.isBlank()) {
            primarySchemaOwner = primarySchemaOwner.trim().toUpperCase(Locale.ROOT);
        } else {
            primarySchemaOwner = null;
        }
    }

    public static PackageDeployDelta none() {
        return new PackageDeployDelta(false, false, null, null, List.of(), false, Map.of());
    }

    public boolean hasPackageTouch() {
        return primaryPackageName != null || !packageNames.isEmpty() || !touchesByPackage.isEmpty();
    }

    /**
     * Narrow flags to a submitted package target. Other packages remain listed in {@link #packageNames()}.
     */
    public PackageDeployDelta focusedOn(String packageName) {
        if (packageName == null || packageName.isBlank()) {
            return this;
        }
        String target = packageName.trim().toUpperCase(Locale.ROOT);
        PackageTouch touch = touchesByPackage.get(target);
        boolean spec = touch != null && touch.specChanged();
        boolean body = touch != null && touch.bodyChanged();
        String schema = touch != null && touch.schemaOwner() != null
                ? touch.schemaOwner()
                : primarySchemaOwner;
        return new PackageDeployDelta(
                spec,
                body,
                target,
                schema,
                packageNames.isEmpty() ? List.of(target) : packageNames,
                inferredFromScript,
                touchesByPackage
        );
    }

    public String changeKind() {
        if (specChanged && bodyChanged) {
            return "SPEC_AND_BODY";
        }
        if (specChanged) {
            return "SPEC_ONLY";
        }
        if (bodyChanged) {
            return "BODY_ONLY";
        }
        return "UNKNOWN";
    }

    public record PackageTouch(String packageName, String schemaOwner, boolean specChanged, boolean bodyChanged) {
        public PackageTouch {
            Objects.requireNonNull(packageName, "packageName");
            packageName = packageName.trim().toUpperCase(Locale.ROOT);
            if (schemaOwner != null && !schemaOwner.isBlank()) {
                schemaOwner = schemaOwner.trim().toUpperCase(Locale.ROOT);
            } else {
                schemaOwner = null;
            }
        }

        public PackageTouch merge(PackageTouch other) {
            if (other == null) {
                return this;
            }
            String schema = this.schemaOwner != null ? this.schemaOwner : other.schemaOwner;
            return new PackageTouch(
                    packageName,
                    schema,
                    this.specChanged || other.specChanged,
                    this.bodyChanged || other.bodyChanged
            );
        }
    }

    public static final class Builder {
        private final Map<String, PackageTouch> touches = new LinkedHashMap<>();
        private boolean inferredFromScript;

        public Builder inferredFromScript(boolean value) {
            this.inferredFromScript = value;
            return this;
        }

        public Builder touch(String packageName, String schemaOwner, boolean spec, boolean body) {
            if (packageName == null || packageName.isBlank()) {
                return this;
            }
            PackageTouch next = new PackageTouch(packageName, schemaOwner, spec, body);
            touches.merge(next.packageName(), next, PackageTouch::merge);
            return this;
        }

        public PackageDeployDelta build() {
            if (touches.isEmpty()) {
                return none();
            }
            List<String> names = new ArrayList<>(touches.keySet());
            PackageTouch primary = touches.values().iterator().next();
            boolean anySpec = touches.values().stream().anyMatch(PackageTouch::specChanged);
            boolean anyBody = touches.values().stream().anyMatch(PackageTouch::bodyChanged);
            return new PackageDeployDelta(
                    anySpec,
                    anyBody,
                    primary.packageName(),
                    primary.schemaOwner(),
                    names,
                    inferredFromScript,
                    touches
            );
        }
    }
}
