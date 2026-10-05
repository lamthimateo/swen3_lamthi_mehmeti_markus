import React from "react";
import Dashboard from "./Dashboard";
import DocumentDetail from "./DocumentDetail";
import "./App.css";

export default function App() {
  const currentPath = window.location.pathname;
  const isDetailPage = currentPath.startsWith("/document/");

  return (
    <div className="app">
      {isDetailPage ? <DocumentDetail /> : <Dashboard />}
    </div>
  );
}
