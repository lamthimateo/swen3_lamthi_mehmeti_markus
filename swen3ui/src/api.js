// Small helpers shared by all pages: logging and turning failed API responses into readable messages.

const PREFIX = "[swen3ui]";

/** Thin wrapper around the console so every log line is recognisable and can be silenced in one place. */
export const log = {
  info: (...args) => console.info(PREFIX, ...args),
  warn: (...args) => console.warn(PREFIX, ...args),
  error: (...args) => console.error(PREFIX, ...args),
};

/**
 * Builds the message to show for a failed response.
 *
 * The REST API answers errors as RFC 9457 problem details ({"status": 404, "detail": "document 99 not found"}),
 * so the server's own explanation is used when there is one. Errors that do not come from the API, such as
 * nginx's 502 page while the backend is down, are not JSON; for those the fallback text plus the status is shown.
 */
export async function errorMessage(response, fallback) {
  let detail = null;
  try {
    const problem = await response.json();
    detail = typeof problem?.detail === "string" ? problem.detail : null;
  } catch {
    // body is not JSON
  }
  log.warn(`${response.status} for ${response.url}`, detail ?? "(no detail)");
  return detail ?? `${fallback} (${response.status})`;
}
