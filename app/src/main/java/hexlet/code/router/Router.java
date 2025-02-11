package hexlet.code.router;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Collections;
import java.util.Arrays;
import java.util.HashMap;
import java.util.regex.Pattern;

public class Router {
    private static final String DATA_KEY = "__data/";
    private static final String DYNAMIC_KEY = "__dkey/";
    private static final String DYNAMIC_KEYS = "__dkeys/";
    private static final Pattern DEFAULT_DYNAMIC_CONSTRAINT = Pattern.compile(".*");
    private static final String DEFAULT_METHOD = "GET";

    public static Map<String, Object> serve(List<Map<String, Object>> routes, Map<String, String> request) {
        Map<String, Object> tree = new HashMap<>();
        for (Map<String, Object> route : routes) {
            makeTree(tree, route);
        }

        String path = (String) request.get("path");
        String method = (String) request.getOrDefault("method", DEFAULT_METHOD);
        return handleRequest(tree, path, method);
    }

    private static void makeTree(Map<String, Object> tree, Map<String, Object> route) {
        String path = (String) route.get("path");
        Map<String, String> constraints = (Map<String, String>) route.getOrDefault("constraints", new HashMap<>());
        String method = (String) route.getOrDefault("method", DEFAULT_METHOD);
        Object handler = route.get("handler");
        Map<String, Object> rest = new HashMap<>(route);
        rest.remove("path");
        rest.remove("constraints");
        rest.remove("method");
        rest.remove("handler");

        List<String> paths = parsePath(path);
        iter(tree, paths, method, handler, constraints, rest);
    }

    private static List<String> parsePath(String path) {
        return Arrays.asList(path.split("/"));
    }

    private static void iter(Map<String, Object> currentTree, List<String> paths,
        String method, Object handler, Map<String, String> constraints, Map<String, Object> rest) {

        if (paths.isEmpty()) {
            Map<String, Object> newData = new HashMap<>();
            newData.put("method", method);
            newData.put("handler", handler);
            newData.putAll(rest);

            if (currentTree.containsKey(DATA_KEY)) {
                List<Map<String, Object>> prevData = (List<Map<String, Object>>) currentTree.get(DATA_KEY);
                prevData.add(newData);
            } else {
                currentTree.put(DATA_KEY, new ArrayList<>(Collections.singletonList(newData)));
            }
            return;
        }

        String currentPath = paths.get(0);
        List<String> restPaths = paths.subList(1, paths.size());

        if (currentTree.containsKey(currentPath)) {
            Map<String, Object> subtree = (Map<String, Object>) currentTree.get(currentPath);
            iter(subtree, restPaths, method, handler, constraints, rest);
        } else if (isDynamicKey(currentPath) && currentTree.containsKey(DYNAMIC_KEY)) {
            Map<String, Object> subtree = (Map<String, Object>) currentTree.get(DYNAMIC_KEY);
            Pattern constraint = Pattern.compile(
                constraints.getOrDefault(getDynamicKey(currentPath), DEFAULT_DYNAMIC_CONSTRAINT.pattern())
            );
            List<Map<String, Object>> keyConstraints = (List<Map<String, Object>>) currentTree.get(DYNAMIC_KEYS);
            keyConstraints.add(Map.of("key", currentPath, "constraint", constraint));
            currentTree.put(DYNAMIC_KEYS, keyConstraints);
            iter(subtree, restPaths, method, handler, constraints, rest);
        } else if (isDynamicKey(currentPath)) {
            Pattern constraint = Pattern.compile(
                constraints.getOrDefault(getDynamicKey(currentPath), DEFAULT_DYNAMIC_CONSTRAINT.pattern())
            );
            currentTree.put(DYNAMIC_KEYS, new ArrayList<>(Collections.singletonList(
                Map.of("key", currentPath, "constraint", constraint)
            )));
            currentTree.put(DYNAMIC_KEY, new HashMap<>());
            iter((Map<String, Object>) currentTree.get(DYNAMIC_KEY), restPaths, method, handler, constraints, rest);
        } else {
            currentTree.put(currentPath, new HashMap<>());
            iter((Map<String, Object>) currentTree.get(currentPath), restPaths, method, handler, constraints, rest);
        }
    }

    private static Map<String, Object> handleRequest(Map<String, Object> tree, String path, String method) {
        List<String> paths = parsePath(path);
        if (paths.isEmpty() && !path.equals("/")) {
            throw new IllegalArgumentException("No such path -- " + path);
        }

        var result = iterRequest(paths, tree, method, new HashMap<>());
        result.put("path", path);
        return result;
    }

    private static Map<String, Object> iterRequest(List<String> currentPaths, Map<String, Object> currentTree,
        String method, Map<String, String> acc) {

        if (currentPaths.isEmpty()) {
            List<Map<String, Object>> handlers = (List<Map<String, Object>>) currentTree.get(DATA_KEY);
            Map<String, Object> route = getHandler(method, handlers, acc);

            if (route == null) {
                throw new IllegalArgumentException("No such path -- " + String.join("/", currentPaths));
            }

            HashMap<String, Object> result = new HashMap<>(route);
            result.put("params", acc);
            return result;
        }

        String currentPath = currentPaths.get(0);
        List<String> restPaths = currentPaths.subList(1, currentPaths.size());

        if (currentTree.containsKey(currentPath)) {
            return iterRequest(restPaths, (Map<String, Object>) currentTree.get(currentPath), method, acc);
        }

        if (currentTree.containsKey(DYNAMIC_KEY)) {
            List<Map<String, Object>> constraints = (List<Map<String, Object>>) currentTree.get(DYNAMIC_KEYS);
            for (Map<String, Object> constraint : constraints) {
                String key = (String) constraint.get("key");
                Pattern pattern = (Pattern) constraint.get("constraint");
                if (currentPath.matches(pattern.pattern())) {
                    String matchKey = getDynamicKey(key);
                    acc.put(matchKey, currentPath);
                    return iterRequest(restPaths, (Map<String, Object>) currentTree.get(DYNAMIC_KEY), method, acc);
                }
            }
        }

        throw new IllegalArgumentException("No such path -- " + String.join("/", currentPaths));
    }

    private static boolean isDynamicKey(String str) {
        return str.startsWith(":");
    }

    private static String getDynamicKey(String key) {
        return key.substring(1);
    }

    private static Map<String, Object> getHandler(String method, List<Map<String, Object>> handlers,
        Map<String, String> params) {

        for (Map<String, Object> handler : handlers) {
            if (handler.get("method").equals(method)
                && matchAllConstraints((Map<String, Pattern>) handler.get("constraints"), params)) {

                return handler;
            }
        }
        return null;
    }

    private static boolean matchAllConstraints(Map<String, Pattern> constraints, Map<String, String> params) {
        if (constraints == null) {
            return true;
        }
        for (String key : constraints.keySet()) {
            if (!params.containsKey(key) || !constraints.get(key).matcher(params.get(key)).matches()) {
                return false;
            }
        }
        return true;
    }
}
