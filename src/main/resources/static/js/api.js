/**
 * API Communication Module
 * Handles all backend communication
 */

const API = {
  // Configuration
  // Prefer deriving the context path from the currently loaded script URL.
  // This works when the app is hosted at /<context>/... as well as at /.
  get contextPath() {
    try {
      const scriptUrl = new URL(document.currentScript?.src || '', window.location.origin);
      // scriptUrl.pathname e.g. "/TheDICOMPolice/js/api.js" or "/js/api.js"
      const path = scriptUrl.pathname || '';
      const idx = path.indexOf('/js/');
      if (idx > 0) {
        const prefix = path.substring(0, idx);
        return prefix === '/' ? '' : prefix;
      }
    } catch (_) {
      // ignore
    }

    // Fallback: first path segment heuristic.
    return window.location.pathname.substring(0, window.location.pathname.indexOf('/', 1)) || '';
  },

  get baseURL() {
    return window.location.origin + this.contextPath;
  },

  get endpoints() {
    return {
      profiles: `${this.baseURL}/validation/v2/profiles`,
      validate: `${this.baseURL}/validation/v2/validate`,
      // Visualizer backend is mounted under /api/visualizer
      parse: `${this.baseURL}/api/visualizer/parse`,
      validateVisualizer: `${this.baseURL}/api/visualizer/validate`
    };
  },

  /**
   * Read a helpful error message from a failed fetch response.
   */
  async readErrorMessage(response, fallbackPrefix = 'Request failed') {
    // Special-case 404: this is often a wrong base URL / missing context path.
    if (response?.status === 404) {
      return `${fallbackPrefix}: 404 Not Found. ` +
        `If this app is deployed under a context path (e.g. /TheDICOMPolice), ` +
        `make sure the page is opened under that path so API calls resolve correctly.`;
    }

    try {
      const contentType = response.headers.get('content-type') || '';
      if (contentType.includes('application/json')) {
        const json = await response.json();
        if (json && typeof json === 'object') {
          if (json.error) {
            return `${fallbackPrefix}: ${json.error}`;
          }
          return `${fallbackPrefix}: ${JSON.stringify(json)}`;
        }
      }

      const text = await response.text();
      if (text) {
        return text;
      }
    } catch (_) {
      // ignore parse issues
    }

    return `${fallbackPrefix}: ${response?.status || ''} ${response?.statusText || ''}`.trim();
  },

  // Load validation profiles
  async loadProfiles() {
    const response = await fetch(this.endpoints.profiles);
    if (!response.ok) {
      throw new Error(await this.readErrorMessage(response, 'Failed to load profiles'));
    }
    return response.json();
  },

  // Validate a DICOM file
  async validateFile(file, profileId) {
    const base64Content = await this.readFileAsBase64(file);

    const validationRequest = {
      validationProfileId: profileId,
      inputs: [
        {
          id: 'uploaded',
          itemId: 'uploaded',
          content: base64Content
        }
      ]
    };

    const response = await fetch(this.endpoints.validate, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(validationRequest)
    });

    if (!response.ok) {
      throw new Error(await this.readErrorMessage(response, 'Validation failed'));
    }

    return response.json();
  },

  /**
   * Validate a DICOM file using the visualizer backend (multipart).
   * Server may auto-detect profile if omitted.
   */
  async validateVisualizerFile(file, profile = null) {
    const formData = new FormData();
    formData.append('file', file);
    if (profile) {
      formData.append('profile', profile);
    }

    const response = await fetch(this.endpoints.validateVisualizer, {
      method: 'POST',
      body: formData
    });

    if (!response.ok) {
      throw new Error(await this.readErrorMessage(response, 'Error validating file'));
    }

    return response.json();
  },

  // Read file as base64
  async readFileAsBase64(file) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => {
        const base64 = reader.result.split(',')[1];
        resolve(base64);
      };
      reader.onerror = reject;
      reader.readAsDataURL(file);
    });
  },

  // Parse a DICOM file for visualization
  async parseFile(file) {
    const formData = new FormData();
    formData.append('file', file);

    const response = await fetch(this.endpoints.parse, {
      method: 'POST',
      body: formData
    });

    if (!response.ok) {
      throw new Error(await this.readErrorMessage(response, 'Error parsing file'));
    }

    return response.json();
  }
};
