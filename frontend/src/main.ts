import { bootstrapApplication } from '@angular/platform-browser';
import { setWorkerUrl } from 'maplibre-gl';
import { appConfig } from './app/app.config';
import { App } from './app/app';

// MapLibre v6 : le worker n'est pas résolu par le bundler Angular — URL explicite.
setWorkerUrl(new URL('maplibre-gl-worker.mjs', document.baseURI).href);

bootstrapApplication(App, appConfig).catch((err) => console.error(err));
