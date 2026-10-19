const COMMON_STATUS_MESSAGES = {
  400: "The request is invalid.",
  401: "You are not authorized to perform this action.",
  403: "You do not have permission to perform this action.",
  404: "The requested resource could not be found.",
  409: "This action conflicts with the current data.",
  500: "The server encountered an error. Please try again.",
  502: "The server is temporarily unavailable. Please try again.",
  503: "The server is temporarily unavailable. Please try again.",
  504: "The server took too long to respond. Please try again.",
};

export function apiErrorMessage(response, fallback, statusMessages = {}) {
  return (
    statusMessages[response.status] ??
    COMMON_STATUS_MESSAGES[response.status] ??
    `${fallback} (${response.status})`
  );
}

export function requestErrorMessage(error, fallback) {
  if (error instanceof TypeError) {
    return "Unable to connect to the server. Check your connection and try again.";
  }
  return error instanceof Error ? error.message : fallback;
}
