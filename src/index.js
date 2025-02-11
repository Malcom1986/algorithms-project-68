import has from 'lodash/has.js';
import get from 'lodash/get.js';

const parsePath = (path) => path.split('/').filter((str) => str !== '');

const isDynamicKey = (str) => str.startsWith(':');
const getDynamicKey = (key) => key.slice(1);

const dataKey = '__data/';
const dynamicKey = '__dkey/';
const dynamicKeys = '__dkeys/';
const defaultDynamicConstraint = /./;
const defaultMethod = 'GET';

const makeTree = (tree, route) => {
  const {
    path, constraints = {}, method = defaultMethod, handler, ...rest
  } = route;

  // Преобразуем строки в регулярные выражения
  const parsedConstraints = Object.fromEntries(
    Object.entries(constraints).map(([key, value]) => [key, new RegExp(value)]),
  );

  const iter = (currentTree, paths) => {
    if (paths.length === 0) {
      const newData = {
        method, handler, ...rest, constraints: parsedConstraints,
      };
      if (has(currentTree, dataKey)) {
        const prevData = currentTree[dataKey];
        return {
          ...currentTree,
          [dataKey]: [...prevData, newData],
        };
      }
      return {
        ...currentTree,
        [dataKey]: [newData],
      };
    }
    const [currentPath, ...restPaths] = paths;
    if (has(currentTree, currentPath)) {
      const subtree = currentTree[currentPath];
      return {
        ...currentTree,
        [currentPath]: iter(subtree, restPaths),
      };
    }
    if (isDynamicKey(currentPath) && has(currentTree, dynamicKey)) {
      const subtree = currentTree[dynamicKey];
      const constraint = get(
        parsedConstraints,
        getDynamicKey(currentPath),
        defaultDynamicConstraint,
      );
      const keyConstraints = currentTree[dynamicKeys];

      return {
        ...currentTree,
        [dynamicKeys]: [...keyConstraints, { key: currentPath, constraint }],
        [dynamicKey]: iter(subtree, restPaths),
      };
    }
    if (isDynamicKey(currentPath)) {
      const constraint = get(
        parsedConstraints,
        getDynamicKey(currentPath),
        defaultDynamicConstraint,
      );
      return {
        ...currentTree,
        [dynamicKeys]: [{ key: currentPath, constraint }],
        [dynamicKey]: iter({}, restPaths),
      };
    }

    return {
      ...currentTree,
      [currentPath]: iter({}, restPaths),
    };
  };

  return iter(tree, parsePath(path));
};

const matchAnyConstraints = (constraints, value) => {
  const result = constraints.find(({ constraint }) => value.match(constraint));
  return result;
};

const matchAllConstraints = (constraints, params) => {
  const keys = Object.keys(constraints);

  return keys.every((key) => {
    if (!has(params, key)) {
      return false;
    }
    const value = params[key];
    const constraint = constraints[key];
    return value.match(constraint);
  });
};

const getHandler = (method, handlers, params) => {
  const result = handlers
    .filter((handler) => handler.method === method)
    .find((handler) => matchAllConstraints(handler.constraints, params));
  return result;
};

export default (routes, request) => {
  const tree = routes.reduce((acc, route) => makeTree(acc, route), {});

  const serve = ({ path, method = defaultMethod }) => {
    const paths = parsePath(path);
    if (paths.length === 0 && path !== '/') {
      throw new Error(`No such path -- ${path}`);
    }

    const iter = (currentPaths, currentTree, acc) => {
      if (currentPaths.length === 0) {
        const handlers = currentTree[dataKey];
        const route = getHandler(method, handlers, acc);

        if (!route) {
          throw new Error(`No such path -- ${path}`);
        }

        return { ...route, params: acc };
      }
      const [currentPath, ...restPaths] = currentPaths;

      if (has(currentTree, currentPath)) {
        return iter(restPaths, currentTree[currentPath], acc);
      }
      if (has(currentTree, dynamicKey)) {
        const constraints = currentTree[dynamicKeys];
        const match = matchAnyConstraints(constraints, currentPath);
        if (match) {
          const matchKey = getDynamicKey(match.key);
          return iter(restPaths, currentTree[dynamicKey], { ...acc, [matchKey]: currentPath });
        }
      }
      throw new Error(`No such path -- ${path}`);
    };

    return iter(paths, tree, {});
  };

  return serve(request);
};
