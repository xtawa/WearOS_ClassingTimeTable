// npm install --no-save jsdom@26.1.0, or set JSDOM_MODULE to its local path.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {JSDOM} = require(process.env.JSDOM_MODULE || 'jsdom');
const page = new JSDOM(fs.readFileSync(path.join(__dirname, '../docs/global-adaptation/metrics.html'), 'utf8'), {runScripts: 'dangerously'});
const aggregate = (files, day=107) => JSON.parse(JSON.stringify(page.window.aggregateMetrics(files, day)));
const event = (name, day, extra={}) => ({event:name, day, ...extra});
const file = (installation, events) => ({format:'classing_product_metrics_v1',installation,events});
assert.equal(aggregate([]).meanSeconds, null);
assert.equal(aggregate([]).mature, 0);
const complete = file('a', [event('CREATION_STARTED',100,{session:'s1'}),event('CREATION_STARTED',100,{session:'s1'}),
    event('IMPORT_CORRECTED',100,{session:'s1',count:2}),event('IMPORT_CORRECTED',100,{session:'abandoned',count:8}),
    event('CREATION_COMPLETED',100,{session:'s1',elapsedMs:90000}),event('CREATION_COMPLETED',100,{session:'s1',elapsedMs:90000}),
    event('CREATION_COMPLETED',100,{session:'no-start',elapsedMs:500}),event('ACTIVE_DAY',107),
    event('SYNC_ATTEMPT',101),event('SYNC_FAILED',101),event('NEXT_CLASS_VIEWED',102)]);
const result=aggregate([complete]);
assert.equal(result.started,1);assert.equal(result.completed,1);assert.equal(result.corrections,2);
assert.equal(result.timedSamples,1);assert.equal(result.meanSeconds,90);assert.equal(result.retained,1);
assert.equal(result.failures,1);assert.equal(result.attempts,1);
assert.equal(aggregate([complete],106).mature,0);
assert.equal(aggregate([complete,complete]).started,1);
assert.equal(aggregate([complete,file('a',[])]).started,0);
assert.equal(aggregate([complete,file('b',[event('CREATION_STARTED',105,{session:'s2'})])]).mature,1);
assert.throws(()=>aggregate([{format:'wrong',events:[]}]),/格式/);
page.window.close();
console.log('PASS: metric cohort maturity, session completion/corrections, duplicate exports and invalid format');
