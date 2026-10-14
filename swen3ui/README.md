# swen3ui

React web UI for the SWEN3 document management system, built with Vite and served by nginx.

## Pages

- **Dashboard** (`/`): upload documents, paginated and sortable document list, view and delete.
- **Document detail** (`/document/{id}`): document info, create labels, attach and remove labels with a weight.

All API calls go to relative `/api/...` URLs.

## Run

**With Docker** (from the repository root):

```bash
docker compose up --build
```

The UI is then at http://localhost/. nginx (`nginx.conf`) serves the built files and forwards `/api/` to the `swen3rest` container.

**Development with live reload** (needs the REST API on `localhost:8081`, e.g. started from the IDE):

```bash
npm install
npm run dev
```

The UI is then at http://localhost:5173. The Vite dev server (`vite.config.js`) forwards `/api` to `localhost:8081`.

## Scripts

| Command | What it does |
| --- | --- |
| `npm run dev` | dev server with live reload on port 5173 |
| `npm run build` | production build into `dist/` |
| `npm run preview` | serve the production build locally |
| `npm run lint` | run ESLint |
