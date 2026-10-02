package com.craftinginterpreters.lox;

import java.util.List;
import java.util.Map;

class LoxClass extends LoxInstance implements LoxCallable {
  final String name;
  final LoxClass superclass;
  final Map<String, LoxFunction> methods;

  LoxClass(
      LoxClass metaclass,
      String name,
      LoxClass superclass,
      Map<String, LoxFunction> methods) {
    super(metaclass);
    this.name = name;
    this.superclass = superclass;
    this.methods = methods;
  }

  // Used when we only need to find the method itself.
  LoxFunction findMethod(String name) {
    LoxFunction method = null;
    LoxClass klass = this;

    while (klass != null) {
      if (klass.methods.containsKey(name)) {
        method = klass.methods.get(name);
      }

      klass = klass.superclass;
    }

    return method;
  }

  // Challenge 2:
  // Finds the topmost method and builds the "inner" chain.
  LoxFunction findMethod(
      LoxInstance instance,
      String name) {

    LoxFunction boundMethod = null;
    LoxClass klass = this;

    while (klass != null) {
      if (klass.methods.containsKey(name)) {
        LoxFunction method =
            klass.methods.get(name);

        boundMethod =
            method.bind(instance, boundMethod);
      }

      klass = klass.superclass;
    }

    return boundMethod;
  }

  @Override
  public Object call(
      Interpreter interpreter,
      List<Object> arguments) {

    LoxInstance instance =
        new LoxInstance(this);

    LoxFunction initializer =
        findMethod(instance, "init");

    if (initializer != null) {
      initializer.call(
          interpreter,
          arguments);
    }

    return instance;
  }

  @Override
  public int arity() {
    LoxFunction initializer =
        findMethod("init");

    if (initializer == null) {
      return 0;
    }

    return initializer.arity();
  }

  @Override
  public String toString() {
    return name;
  }
}