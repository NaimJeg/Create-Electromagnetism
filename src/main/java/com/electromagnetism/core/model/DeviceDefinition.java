package com.electromagnetism.core.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Terminal order and local power reference. All currents (A) flow INTO the device. */
public record DeviceDefinition(String name, List<String> terminals, int referenceTerminal) {
    public DeviceDefinition {
        Objects.requireNonNull(name);
        if (name.isBlank()) throw new IllegalArgumentException("Missing model name");
        terminals = List.copyOf(terminals);
        if (terminals.size() < 2 || terminals.stream().anyMatch(String::isBlank)
                || new HashSet<>(terminals).size() != terminals.size())
            throw new IllegalArgumentException("Terminal names must be nonempty and unique");
        if (referenceTerminal < 0 || referenceTerminal >= terminals.size())
            throw new IllegalArgumentException("Invalid reference terminal");
    }
}
