package com.example.state_machine.service.advance.preconditions;

import com.example.state_machine.model.ProcessInstance;

import java.util.Collection;
import java.util.Map;

abstract class BasePrecondition {

    protected Object read(Map<String, Object> payload, ProcessInstance pi, String key) {
        Object v = payload != null ? payload.get(key) : null;
        if (v != null) return v;
        return pi.getVariables() != null ? pi.getVariables().get(key) : null;
    }

    protected boolean isEmpty(Object v) {
        if (v == null) return true;
        if (v instanceof String s) return s.isBlank();
        if (v instanceof Collection<?> c) return c.isEmpty();
        return false;
    }

    protected boolean asBoolean(Object v, boolean def) {
        if (v instanceof Boolean b) return b;
        if (v == null) return def;
        return Boolean.parseBoolean(String.valueOf(v));
    }

    protected boolean in(Object v, String... allowedUpper) {
        if (v == null) return false;
        String s = String.valueOf(v).trim().toUpperCase();
        for (String a : allowedUpper) if (s.equals(a)) return true;
        return false;
    }
}
