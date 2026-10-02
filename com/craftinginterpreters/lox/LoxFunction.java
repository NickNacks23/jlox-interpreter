package com.craftinginterpreters.lox;

import java.util.List;

class LoxFunction implements LoxCallable {
  private final String name;
  private final Expr.Function declaration;
  private final Environment closure;
  private final boolean isInitializer;

  LoxFunction(
      String name,
      Expr.Function declaration,
      Environment closure) {
    this(name, declaration, closure, false);
  }

  LoxFunction(
      String name,
      Expr.Function declaration,
      Environment closure,
      boolean isInitializer) {
    this.name = name;
    this.declaration = declaration;
    this.closure = closure;
    this.isInitializer = isInitializer;
  }

  

LoxFunction bind(LoxInstance instance) {
  return bind(instance, null);
}

LoxFunction bind(
    LoxInstance instance,
    LoxFunction inner) {

  Environment environment =
      new Environment(closure);

  environment.define("this", instance);

  if (inner == null) {
    environment.define(
        "inner",
        new LoxCallable() {
          @Override
          public int arity() {
            return 0;
          }

          @Override
          public Object call(
              Interpreter interpreter,
              List<Object> arguments) {
            return null;
          }

          @Override
          public String toString() {
            return "<inner>";
          }
        });
  } else {
    environment.define("inner", inner);
  }

  return new LoxFunction(
      name,
      declaration,
      environment,
      isInitializer);
}


  @Override
  public int arity() {
    if (declaration.parameters == null) return 0;
    return declaration.parameters.size();
  }

  @Override
  public Object call(
      Interpreter interpreter,
      List<Object> arguments) {

    Environment environment = new Environment(closure);

    if (declaration.parameters != null) {
      for (int i = 0; i < declaration.parameters.size(); i++) {
        environment.define(
            declaration.parameters.get(i).lexeme,
            arguments.get(i));
      }
    }

    try {
      interpreter.executeBlock(declaration.body, environment);
    } catch (Return returnValue) {
      if (isInitializer) {
        return closure.getAt(0, 0);
      }

      return returnValue.value;
    }

    if (isInitializer) {
      return closure.getAt(0, 0);
    }

    return null;
  }

  boolean isGetter() {
    return declaration.parameters == null;
  }

  @Override
  public String toString() {
    if (name == null) return "<fn>";
    return "<fn " + name + ">";
  }
}