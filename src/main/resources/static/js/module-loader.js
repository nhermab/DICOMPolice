/**
 * Module Loader Helper
 * Provides utilities for loading and managing JavaScript modules
 */

const ModuleLoader = {
  /**
   * Track loaded modules
   */
  loadedModules: new Set(),

  /**
   * Load a script dynamically
   * @param {string} src - Script source URL
   * @param {boolean} async - Whether to load asynchronously
   * @returns {Promise<void>}
   */
  loadScript(src, async = true) {
    return new Promise((resolve, reject) => {
      if (this.loadedModules.has(src)) {
        resolve();
        return;
      }

      const script = document.createElement('script');
      script.src = src;
      script.async = async;

      script.onload = () => {
        this.loadedModules.add(src);
        resolve();
      };

      script.onerror = () => {
        reject(new Error(`Failed to load script: ${src}`));
      };

      document.head.appendChild(script);
    });
  },

  /**
   * Load multiple scripts in sequence
   * @param {string[]} scripts - Array of script URLs
   * @returns {Promise<void>}
   */
  async loadScripts(scripts) {
    for (const script of scripts) {
      await this.loadScript(script);
    }
  },

  /**
   * Load multiple scripts in parallel
   * @param {string[]} scripts - Array of script URLs
   * @returns {Promise<void[]>}
   */
  loadScriptsParallel(scripts) {
    return Promise.all(scripts.map(script => this.loadScript(script)));
  },

  /**
   * Check if a module is loaded
   * @param {string} moduleName - Name of the global module variable
   * @returns {boolean}
   */
  isModuleLoaded(moduleName) {
    return typeof window[moduleName] !== 'undefined';
  },

  /**
   * Wait for a module to be loaded
   * @param {string} moduleName - Name of the global module variable
   * @param {number} timeout - Timeout in milliseconds
   * @returns {Promise<any>}
   */
  waitForModule(moduleName, timeout = 5000) {
    return new Promise((resolve, reject) => {
      if (this.isModuleLoaded(moduleName)) {
        resolve(window[moduleName]);
        return;
      }

      const startTime = Date.now();
      const interval = setInterval(() => {
        if (this.isModuleLoaded(moduleName)) {
          clearInterval(interval);
          resolve(window[moduleName]);
        } else if (Date.now() - startTime > timeout) {
          clearInterval(interval);
          reject(new Error(`Timeout waiting for module: ${moduleName}`));
        }
      }, 50);
    });
  }
};

