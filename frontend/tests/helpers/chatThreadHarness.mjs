import { readFile } from "node:fs/promises";
import { stripTypeScriptTypes } from "node:module";

// Exercise the production hook without adding a DOM/test-renderer dependency. The
// runner preserves state/ref slots, dependency comparisons, and post-render effects;
// tests explicitly flush renders and control the network promises that create races.
let rendering;
const equalDeps = (left, right) => left && right
  && left.length === right.length && left.every((value, index) => Object.is(value, right[index]));

export function useState(initial) {
  const runner = rendering;
  const index = runner.index++;
  if (!runner.slots[index]) {
    const slot = { value: typeof initial === "function" ? initial() : initial };
    slot.set = next => {
      const value = typeof next === "function" ? next(slot.value) : next;
      if (!Object.is(value, slot.value)) {
        slot.value = value;
        runner.dirty = true;
      }
    };
    runner.slots[index] = slot;
  }
  const slot = runner.slots[index];
  return [slot.value, slot.set];
}

export function useRef(initial) {
  const runner = rendering;
  const index = runner.index++;
  return runner.slots[index] ??= { current: initial };
}

export function useCallback(callback, deps) {
  const runner = rendering;
  const index = runner.index++;
  if (!equalDeps(runner.slots[index]?.deps, deps)) runner.slots[index] = { callback, deps };
  return runner.slots[index].callback;
}

export function useEffect(effect, deps) {
  const runner = rendering;
  const index = runner.index++;
  if (!equalDeps(runner.slots[index]?.deps, deps)) {
    const previous = runner.slots[index];
    const slot = { deps };
    runner.slots[index] = slot;
    runner.effects.push(() => {
      previous?.cleanup?.();
      slot.cleanup = effect();
    });
  }
}

const hookFile = new URL("../../src/chat/useChatThread.ts", import.meta.url);
const compiled = stripTypeScriptTypes(await readFile(hookFile, "utf8"))
  .replace('from "react"', `from ${JSON.stringify(import.meta.url)}`)
  .replace('from "../lib/chatMessages"', `from ${JSON.stringify(new URL("../../src/lib/chatMessages.ts", import.meta.url).href)}`);
// Defer the import until this module's primitives have finished initializing.
const hookUrl = `data:text/javascript;base64,${Buffer.from(compiled).toString("base64")}`;

export async function createChatThread(source) {
  const { useChatThread } = await import(hookUrl);
  return createRenderer(useChatThread, source);
}

/** Render a production hook or a single component with controlled async effects. */
export async function createRenderer(render, source) {
  const runner = {
    source, slots: [], index: 0, effects: [], dirty: true, current: undefined,
    render() {
      this.index = 0;
      this.effects = [];
      this.dirty = false;
      rendering = this;
      try { this.current = render(this.source); }
      finally { rendering = undefined; }
      for (const effect of this.effects) effect();
    },
    async flush() {
      // Includes promise continuations triggered by effects, but never waits for
      // unresolved network requests. Fail deterministically on a render loop.
      for (let turn = 0; turn < 30; turn++) {
        await Promise.resolve();
        if (this.dirty) this.render();
      }
      if (this.dirty) throw new Error("Hook did not settle after 30 render turns");
    },
    async setViewing(viewing) {
      this.source = { ...this.source, viewing };
      this.dirty = true;
      await this.flush();
    },
    dispose() {
      for (const slot of this.slots) slot?.cleanup?.();
    }
  };
  await runner.flush();
  return runner;
}
