import { describe, expect, it } from "vitest";
import { formatCurrency } from "../assets/js/main.js";

describe("formatCurrency", () => {
  it("formats numbers as USD", () => {
    expect(formatCurrency(1250)).toBe("$1,250.00");
  });
});
