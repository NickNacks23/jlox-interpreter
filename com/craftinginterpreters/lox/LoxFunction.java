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
    Environment environment = new Environment(closure);
    environment.define("this", instance);

    return new LoxFunction(
        name,
        declaration,
        environment,
        isInitializer);
  }

  @Override
  public int arity() {
    return declaration.parameters.size();
  }

  @Override
  public Object call(
      Interpreter interpreter,
      List<Object> arguments) {

    Environment environment = new Environment(closure);

    for (int i = 0; i < declaration.parameters.size(); i++) {
      environment.define(
          declaration.parameters.get(i).lexeme,
          arguments.get(i));
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

  @Override
  public String toString() {
    if (name == null) return "<fn>";
    return "<fn " + name + ">";
  }
}