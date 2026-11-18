import Dashboard from "./Dashboard";
import DocumentDetail from "./DocumentDetail";
import Search from "./Search";

export default function App() {
  const currentPath = window.location.pathname;

  let page = <Dashboard />;
  if (currentPath.startsWith("/document/")) {
    page = <DocumentDetail />;
  } else if (currentPath === "/search") {
    page = <Search />;
  }

  return <div className="app">{page}</div>;
}
