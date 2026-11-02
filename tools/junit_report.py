import sys, glob, xml.etree.ElementTree as ET
for f in sorted(glob.glob('app/build/test-results/testDebugUnitTest/*.xml')):
    r = ET.parse(f).getroot()
    print(f"::notice::TESTS {r.get('name')}: tests={r.get('tests')} failures={r.get('failures')} errors={r.get('errors')} skipped={r.get('skipped')}")
    for tc in r.iter('testcase'):
        for bad in list(tc.findall('failure')) + list(tc.findall('error')):
            msg = (bad.get('message') or '').replace('\n', ' ')[:300]
            print(f"::warning::FAILED {tc.get('name')[:120]} :: {msg}")
