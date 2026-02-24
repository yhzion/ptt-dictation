import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { Settings } from "./Settings";

describe("Settings", () => {
  it("shows server port", () => {
    render(<Settings port={9876} />);
    expect(screen.getByText(/9876/)).toBeInTheDocument();
  });
  it("shows server status label", () => {
    render(<Settings port={9876} />);
    expect(screen.getByText(/서버 포트/)).toBeInTheDocument();
  });
});
