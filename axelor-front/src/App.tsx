import { useEffect } from "react";

import { ThemeProvider } from "@axelor/ui";

import { useAppLang } from "./hooks/use-app-lang";
import { useAppThemeOption } from "./hooks/use-app-theme";
import { Routes } from "./routes";

import "./styles/global.scss";

import { insertFromHTML } from "./utils/eduFlowHTMLInjector.ts";

function App() {
  const { theme, options } = useAppThemeOption();
  const { dir, lang } = useAppLang();

  useEffect(() => {
    document.documentElement.lang = lang;
    document.documentElement.dir = dir;
  }, [dir, lang]);

  
  useEffect(() => {
    insertFromHTML(document.head, `${import.meta.env.BASE_URL}includes/head.start.include.html`, "start");
    insertFromHTML(document.head, `${import.meta.env.BASE_URL}includes/head.end.include.html`, "end");
    insertFromHTML(document.body, `${import.meta.env.BASE_URL}includes/body.start.include.html`, "start");
    insertFromHTML(document.body, `${import.meta.env.BASE_URL}includes/body.end.include.html`, "end");
  }, []);

  
  return (
    <ThemeProvider dir={dir} theme={theme} options={options}>
      <Routes />
    </ThemeProvider>
  );
}

export default App;
