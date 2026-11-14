import { defineConfig } from '@playwright/test';
import { dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
const root=dirname(fileURLToPath(import.meta.url));
export default defineConfig({testDir:'./e2e',timeout:30000,fullyParallel:false,workers:1,reporter:[['list'],['html',{open:'never'}]],use:{baseURL:'http://127.0.0.1:4173',viewport:{width:1280,height:1000},launchOptions:process.env.CLASSING_BROWSER_PATH?{executablePath:process.env.CLASSING_BROWSER_PATH}:{},trace:'retain-on-failure'},webServer:{command:'node server.mjs --port 4173',cwd:root,url:'http://127.0.0.1:4173',reuseExistingServer:!process.env.CI}});
