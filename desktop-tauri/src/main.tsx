import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { SettingsWindow } from "@/windows/SettingsWindow.tsx";
import { AboutWindow } from "@/windows/AboutWindow.tsx";

// Disable default right-click context menu (web container controls)
document.addEventListener("contextmenu", (e) => {
  e.preventDefault();
});

const currentWindow = getCurrentWindow();
const RootComponent = (() => {
  switch (currentWindow.label) {
    case "main":
      return App;
    case "settings":
      return SettingsWindow;
    case "about":
      return AboutWindow;
    default:
      return () => <div>Unknown window</div>;
  }
})();

ReactDOM.createRoot(document.getElementById("root") as HTMLElement).render(
  <React.StrictMode>
    <RootComponent />
  </React.StrictMode>,
);
