# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import argparse
import os
import zipfile
import codecs
import tarfile
import zipfile
import threading
import traceback
import datetime
import time
import subprocess
import signal
import shutil
import hashlib
import http.client
import re
import urllib.request, urllib.error, urllib.parse
import urllib.parse
import sys
import html.parser
from collections import defaultdict
import xml.etree.ElementTree as ET
import filecmp
import platform
import checkJavaDocs
import checkJavadocLinks
import io
import codecs
import textwrap
from collections import namedtuple

# This tool expects to find /lucene and /solr off the base URL.  You
# must have a working gpg, tar, unzip in your path.  This has been
# tested on Linux and on Cygwin under Windows 7.

cygwin = platform.system().lower().startswith('cygwin')
cygwinWindowsRoot = os.popen('cygpath -w /').read().strip().replace('\\','/') if cygwin else ''

def unshortenURL(url):
  parsed = urllib.parse.urlparse(url)
  if parsed[0] in ('http', 'https'):
    h = http.client.HTTPConnection(parsed.netloc)
    h.request('HEAD', parsed.path)
    response = h.getresponse()
    if int(response.status/100) == 3 and response.getheader('Location'):
      return response.getheader('Location')
  return url

# TODO
#   + verify KEYS contains key that signed the release
#   + make sure changes HTML looks ok
#   - verify license/notice of all dep jars
#   - check maven
#   - check JAR manifest version
#   - check license/notice exist
#   - check no "extra" files
#   - make sure jars exist inside bin release
#   - run "ant test"
#   - make sure docs exist
#   - use java5 for lucene/modules

reHREF = re.compile('<a href="(.*?)">(.*?)</a>')

# Set to False to avoid re-downloading the packages...
FORCE_CLEAN = True

def getHREFs(urlString):

  # Deref any redirects
  while True:
    url = urllib.parse.urlparse(urlString)
    h = http.client.HTTPConnection(url.netloc)
    h.request('GET', url.path)
    r = h.getresponse()
    newLoc = r.getheader('location')
    if newLoc is not None:
      urlString = newLoc
    else:
      break

  links = []
  try:
    html = load(urlString)
  except:
    print('\nFAILED to open url %s' % urlString)
    traceback.print_exc()
    raise

  for subUrl, text in reHREF.findall(html):
    fullURL = urllib.parse.urljoin(urlString, subUrl)
    links.append((text, fullURL))
  return links

def download(name, urlString, tmpDir, quiet=False):
  startTime = time.time()
  fileName = '%s/%s' % (tmpDir, name)
  if not FORCE_CLEAN and os.path.exists(fileName):
    if not quiet and fileName.find('.asc') == -1:
      print('    already done: %.1f MB' % (os.path.getsize(fileName)/1024./1024.))
    return
  try:
    attemptDownload(urlString, fileName)
  except Exception as e:
    print('Retrying download of url %s after exception: %s' % (urlString, e))
    try:
      attemptDownload(urlString, fileName)
    except Exception as e:
      raise RuntimeError('failed to download url "%s"' % urlString) from e
  if not quiet and fileName.find('.asc') == -1:
    t = time.time()-startTime
    sizeMB = os.path.getsize(fileName)/1024./1024.
    print('    %.1f MB in %.2f sec (%.1f MB/sec)' % (sizeMB, t, sizeMB/t))

def attemptDownload(urlString, fileName):
  fIn = urllib.request.urlopen(urlString)
  fOut = open(fileName, 'wb')
  success = False
  try:
    while True:
      s = fIn.read(65536)
      if s == b'':
        break
      fOut.write(s)
    fOut.close()
    fIn.close()
    success = True
  finally:
    fIn.close()
    fOut.close()
    if not success:
      os.remove(fileName)

def load(urlString):
  try:
    content = urllib.request.urlopen(urlString).read().decode('utf-8')
  except Exception as e:
    print('Retrying download of url %s after exception: %s' % (urlString, e))
    content = urllib.request.urlopen(urlString).read().decode('utf-8')
  return content

def noJavaPackageClasses(desc, file):
  with zipfile.ZipFile(file) as z2:
    for name2 in z2.namelist():
      if name2.endswith('.class') and (name2.startswith('java/') or name2.startswith('javax/')):
        raise RuntimeError('%s contains sheisty class "%s"' %  (desc, name2))

def decodeUTF8(bytes):
  return codecs.getdecoder('UTF-8')(bytes)[0]

MANIFEST_FILE_NAME = 'META-INF/MANIFEST.MF'
NOTICE_FILE_NAME = 'META-INF/NOTICE.txt'
LICENSE_FILE_NAME = 'META-INF/LICENSE.txt'

def checkJARMetaData(desc, jarFile, svnRevision, version):

  with zipfile.ZipFile(jarFile, 'r') as z:
    for name in (MANIFEST_FILE_NAME, NOTICE_FILE_NAME, LICENSE_FILE_NAME):
      try:
        # The Python docs state a KeyError is raised ... so this None
        # check is just defensive:
        if z.getinfo(name) is None:
          raise RuntimeError('%s is missing %s' % (desc, name))
      except KeyError:
        raise RuntimeError('%s is missing %s' % (desc, name))

    s = decodeUTF8(z.read(MANIFEST_FILE_NAME))

    for verify in (
            'Specification-Vendor: The Apache Software Foundation',
            'Implementation-Vendor: The Apache Software Foundation',
      # Make sure 1.7 compiler was used to build release bits:
      'X-Compile-Source-JDK: 1.7',
            # Make sure 1.8 ant was used to build release bits: (this will match 1.8+)
            'Ant-Version: Apache Ant 1.8',
      # Make sure .class files are 1.7 format:
      'X-Compile-Target-JDK: 1.7',
            'Specification-Version: %s' % version,
      # Make sure the release was compiled with 1.7:
      'Created-By: 1.7'):
      if s.find(verify) == -1:
        raise RuntimeError('%s is missing "%s" inside its META-INF/MANIFEST.MF' % \
                           (desc, verify))

    if svnRevision != 'skip':
      # Make sure this matches the version and svn revision we think we are releasing:
      verifyRevision = 'Implementation-Version: %s %s ' % (version, svnRevision)
      if s.find(verifyRevision) == -1:
        raise RuntimeError('%s is missing "%s" inside its META-INF/MANIFEST.MF (wrong svn revision?)' % \
                           (desc, verifyRevision))

    notice = decodeUTF8(z.read(NOTICE_FILE_NAME))
    license = decodeUTF8(z.read(LICENSE_FILE_NAME))

    idx = desc.find('inside WAR file')
    if idx != -1:
      desc2 = desc[:idx]
    else:
      desc2 = desc

    justFileName = os.path.split(desc2)[1]

    if justFileName.lower().find('solr') != -1:
      if SOLR_LICENSE is None:
        raise RuntimeError('BUG in smokeTestRelease!')
      if SOLR_NOTICE is None:
        raise RuntimeError('BUG in smokeTestRelease!')
      if notice != SOLR_NOTICE:
        raise RuntimeError('%s: %s contents doesn\'t match main NOTICE.txt' % \
                           (desc, NOTICE_FILE_NAME))
      if license != SOLR_LICENSE:
        raise RuntimeError('%s: %s contents doesn\'t match main LICENSE.txt' % \
                           (desc, LICENSE_FILE_NAME))
    else:
      if LUCENE_LICENSE is None:
        raise RuntimeError('BUG in smokeTestRelease!')
      if LUCENE_NOTICE is None:
        raise RuntimeError('BUG in smokeTestRelease!')
      if notice != LUCENE_NOTICE:
        raise RuntimeError('%s: %s contents doesn\'t match main NOTICE.txt' % \
                           (desc, NOTICE_FILE_NAME))
      if license != LUCENE_LICENSE:
        raise RuntimeError('%s: %s contents doesn\'t match main LICENSE.txt' % \
                           (desc, LICENSE_FILE_NAME))

def normSlashes(path):
  return path.replace(os.sep, '/')

def checkAllJARs(topDir, project, svnRevision, version, tmpDir, baseURL):
  print('    verify JAR metadata/identity/no javax.* or java.* classes...')
  if project == 'solr':
    luceneDistFilenames = dict()
    for file in getBinaryDistFiles('lucene', tmpDir, version, baseURL):
      luceneDistFilenames[os.path.basename(file)] = file
  for root, dirs, files in os.walk(topDir):

    normRoot = normSlashes(root)

    if project == 'solr' and normRoot.endswith('/server/lib'):
      # Solr's example intentionally ships servlet JAR:
      continue

    for file in files:
      if file.lower().endswith('.jar'):
        if project == 'solr':
          if (normRoot.endswith('/contrib/dataimporthandler-extras/lib') and (file.startswith('javax.mail-') or file.startswith('activation-'))) or (normRoot.endswith('/test-framework/lib') and file.startswith('jersey-')):
            print('      **WARNING**: skipping check of %s/%s: it has javax.* classes' % (root, file))
            continue
        else:
          if normRoot.endswith('/replicator/lib') and file.startswith('javax.servlet'):
            continue
        fullPath = '%s/%s' % (root, file)
        noJavaPackageClasses('JAR file "%s"' % fullPath, fullPath)
        if file.lower().find('lucene') != -1 or file.lower().find('solr') != -1:
          checkJARMetaData('JAR file "%s"' % fullPath, fullPath, svnRevision, version)
        if project == 'solr' and file.lower().find('lucene') != -1:
          jarFilename = os.path.basename(file)
          if jarFilename not in luceneDistFilenames:
            raise RuntimeError('Artifact %s is not present in Lucene binary distribution' % fullPath)
          identical = filecmp.cmp(fullPath, luceneDistFilenames[jarFilename], shallow=False)
          if not identical:
            raise RuntimeError('Artifact %s is not identical to %s in Lucene binary distribution'
                               % (fullPath, luceneDistFilenames[jarFilename]))


def checkSolrWAR(warFileName, svnRevision, version, tmpDir, baseURL):

  """
  Crawls for JARs inside the WAR and ensures there are no classes
  under java.* or javax.* namespace.
  """

  print('    verify WAR metadata/contained JAR identity/no javax.* or java.* classes...')

  checkJARMetaData(warFileName, warFileName, svnRevision, version)

  distFilenames = dict()
  for file in getBinaryDistFiles('lucene', tmpDir, version, baseURL):
    distFilenames[os.path.basename(file)] = file

  with zipfile.ZipFile(warFileName, 'r') as z:
    for name in z.namelist():
      if name.endswith('.jar'):
        jarInsideWarContents = z.read(name)
        noJavaPackageClasses('JAR file %s inside WAR file %s' % (name, warFileName),
                             io.BytesIO(jarInsideWarContents))
        if name.lower().find('lucene') != -1 or name.lower().find('solr') != -1:
          checkJARMetaData('JAR file %s inside WAR file %s' % (name, warFileName),
                           io.BytesIO(jarInsideWarContents),
                           svnRevision,
                           version)
        if name.lower().find('lucene') != -1:
          jarInsideWarFilename = os.path.basename(name)
          if jarInsideWarFilename not in distFilenames:
            raise RuntimeError('Artifact %s in %s is not present in Lucene binary distribution'
                               % (name, warFileName))
          distJarName = distFilenames[jarInsideWarFilename]
          with open(distJarName, "rb", buffering=0) as distJarFile:
            distJarContents = distJarFile.readall()
          if jarInsideWarContents != distJarContents:
            raise RuntimeError('Artifact %s in %s is not identical to %s in Lucene binary distribution'
                               % (name, warFileName, distJarName))


def checkSigs(project, urlString, version, tmpDir, isSigned):

  print('  test basics...')
  ents = getDirEntries(urlString)
  artifact = None
  keysURL = None
  changesURL = None
  mavenURL = None
  expectedSigs = []
  if isSigned:
    expectedSigs.append('asc')
  expectedSigs.extend(['md5', 'sha1'])

  artifacts = []
  for text, subURL in ents:
    if text == 'KEYS':
      keysURL = subURL
    elif text == 'maven/':
      mavenURL = subURL
    elif text.startswith('changes'):
      if text not in ('changes/', 'changes-%s/' % version):
        raise RuntimeError('%s: found %s vs expected changes-%s/' % (project, text, version))
      changesURL = subURL
    elif artifact == None:
      artifact = text
      artifactURL = subURL
      if project == 'solr':
        expected = 'solr-%s' % version
      else:
        expected = 'lucene-%s' % version
      if not artifact.startswith(expected):
        raise RuntimeError('%s: unknown artifact %s: expected prefix %s' % (project, text, expected))
      sigs = []
    elif text.startswith(artifact + '.'):
      sigs.append(text[len(artifact)+1:])
    else:
      if sigs != expectedSigs:
        raise RuntimeError('%s: artifact %s has wrong sigs: expected %s but got %s' % (project, artifact, expectedSigs, sigs))
      artifacts.append((artifact, artifactURL))
      artifact = text
      artifactURL = subURL
      sigs = []

  if sigs != []:
    artifacts.append((artifact, artifactURL))
    if sigs != expectedSigs:
      raise RuntimeError('%s: artifact %s has wrong sigs: expected %s but got %s' % (project, artifact, expectedSigs, sigs))

  if project == 'lucene':
    expected = ['lucene-%s-src.tgz' % version,
                'lucene-%s.tgz' % version,
                'lucene-%s.zip' % version]
  else:
    expected = ['solr-%s-src.tgz' % version,
                'solr-%s.tgz' % version,
                'solr-%s.zip' % version]

  actual = [x[0] for x in artifacts]
  if expected != actual:
    raise RuntimeError('%s: wrong artifacts: expected %s but got %s' % (project, expected, actual))

  if keysURL is None:
    raise RuntimeError('%s is missing KEYS' % project)

  print('  get KEYS')
  download('%s.KEYS' % project, keysURL, tmpDir)

  keysFile = '%s/%s.KEYS' % (tmpDir, project)

  # Set up clean gpg world; import keys file:
  gpgHomeDir = '%s/%s.gpg' % (tmpDir, project)
  if os.path.exists(gpgHomeDir):
    shutil.rmtree(gpgHomeDir)
  os.makedirs(gpgHomeDir, 0o700)
  run('gpg --homedir %s --import %s' % (gpgHomeDir, keysFile),
      '%s/%s.gpg.import.log' % (tmpDir, project))

  if mavenURL is None:
    raise RuntimeError('%s is missing maven' % project)

  if changesURL is None:
    raise RuntimeError('%s is missing changes-%s' % (project, version))
  testChanges(project, version, changesURL)

  for artifact, urlString in artifacts:
    print('  download %s...' % artifact)
    download(artifact, urlString, tmpDir)
    verifyDigests(artifact, urlString, tmpDir)

    if isSigned:
      print('    verify sig')
      # Test sig (this is done with a clean brand-new GPG world)
      download(artifact + '.asc', urlString + '.asc', tmpDir)
      sigFile = '%s/%s.asc' % (tmpDir, artifact)
      artifactFile = '%s/%s' % (tmpDir, artifact)
      logFile = '%s/%s.%s.gpg.verify.log' % (tmpDir, project, artifact)
      run('gpg --homedir %s --verify %s %s' % (gpgHomeDir, sigFile, artifactFile),
          logFile)
      # Forward any GPG warnings, except the expected one (since it's a clean world)
      f = open(logFile, encoding='UTF-8')
      for line in f.readlines():
        if line.lower().find('warning') != -1 \
                and line.find('WARNING: This key is not certified with a trusted signature') == -1:
          print('      GPG: %s' % line.strip())
      f.close()

      # Test trust (this is done with the real users config)
      run('gpg --import %s' % (keysFile),
          '%s/%s.gpg.trust.import.log' % (tmpDir, project))
      print('    verify trust')
      logFile = '%s/%s.%s.gpg.trust.log' % (tmpDir, project, artifact)
      run('gpg --verify %s %s' % (sigFile, artifactFile), logFile)
      # Forward any GPG warnings:
      f = open(logFile, encoding='UTF-8')
      for line in f.readlines():
        if line.lower().find('warning') != -1:
          print('      GPG: %s' % line.strip())
      f.close()

def testChanges(project, version, changesURLString):
  print('  check changes HTML...')
  changesURL = None
  for text, subURL in getDirEntries(changesURLString):
    if text == 'Changes.html':
      changesURL = subURL

  if changesURL is None:
    raise RuntimeError('did not see Changes.html link from %s' % changesURLString)

  s = load(changesURL)
  checkChangesContent(s, version, changesURL, project, True)

def testChangesText(dir, version, project):
  "Checks all CHANGES.txt under this dir."
  for root, dirs, files in os.walk(dir):

    # NOTE: O(N) but N should be smallish:
    if 'CHANGES.txt' in files:
      fullPath = '%s/CHANGES.txt' % root
      #print 'CHECK %s' % fullPath
      checkChangesContent(open(fullPath, encoding='UTF-8').read(), version, fullPath, project, False)

reChangesSectionHREF = re.compile('<a id="(.*?)".*?>(.*?)</a>', re.IGNORECASE)
reUnderbarNotDashHTML = re.compile(r'<li>(\s*(LUCENE|SOLR)_\d\d\d\d+)')
reUnderbarNotDashTXT = re.compile(r'\s+((LUCENE|SOLR)_\d\d\d\d+)', re.MULTILINE)
def checkChangesContent(s, version, name, project, isHTML):

  if isHTML and s.find('Release %s' % version) == -1:
    raise RuntimeError('did not see "Release %s" in %s' % (version, name))

  if isHTML:
    r = reUnderbarNotDashHTML
  else:
    r = reUnderbarNotDashTXT

  m = r.search(s)
  if m is not None:
    raise RuntimeError('incorrect issue (_ instead of -) in %s: %s' % (name, m.group(1)))

  if s.lower().find('not yet released') != -1:
    raise RuntimeError('saw "not yet released" in %s' % name)

  if not isHTML:
    if project == 'lucene':
      sub = 'Lucene %s' % version
    else:
      sub = version

    if s.find(sub) == -1:
      # benchmark never seems to include release info:
      if name.find('/benchmark/') == -1:
        raise RuntimeError('did not see "%s" in %s' % (sub, name))

  if isHTML:
    # Make sure a section only appears once under each release:
    seenIDs = set()
    seenText = set()

    release = None
    for id, text in reChangesSectionHREF.findall(s):
      if text.lower().startswith('release '):
        release = text[8:].strip()
        seenText.clear()
      if id in seenIDs:
        raise RuntimeError('%s has duplicate section "%s" under release "%s"' % (name, text, release))
      seenIDs.add(id)
      if text in seenText:
        raise RuntimeError('%s has duplicate section "%s" under release "%s"' % (name, text, release))
      seenText.add(text)

reUnixPath = re.compile(r'\b[a-zA-Z_]+=(?:"(?:\\"|[^"])*"' + '|(?:\\\\.|[^"\'\\s])*' + r"|'(?:\\'|[^'])*')" \
                        + r'|(/(?:\\.|[^"\'\s])*)' \
                        + r'|("/(?:\\.|[^"])*")'   \
                        + r"|('/(?:\\.|[^'])*')")

def unix2win(matchobj):
  if matchobj.group(1) is not None: return cygwinWindowsRoot + matchobj.group()
  if matchobj.group(2) is not None: return '"%s%s' % (cygwinWindowsRoot, matchobj.group().lstrip('"'))
  if matchobj.group(3) is not None: return "'%s%s" % (cygwinWindowsRoot, matchobj.group().lstrip("'"))
  return matchobj.group()

def cygwinifyPaths(command):
  # The problem: Native Windows applications running under Cygwin
  # (e.g. Ant, which isn't available as a Cygwin package) can't
  # handle Cygwin's Unix-style paths.  However, environment variable
  # values are automatically converted, so only paths outside of
  # environment variable values should be converted to Windows paths.
  # Assumption: all paths will be absolute.
  if '; ant ' in command: command = reUnixPath.sub(unix2win, command)
  return command

def printFileContents(fileName):

  # Assume log file was written in system's default encoding, but
  # even if we are wrong, we replace errors ... the ASCII chars
  # (which is what we mostly care about eg for the test seed) should
  # still survive:
  txt = codecs.open(fileName, 'r', encoding=sys.getdefaultencoding(), errors='replace').read()

  # Encode to our output encoding (likely also system's default
  # encoding):
  bytes = txt.encode(sys.stdout.encoding, errors='replace')

  # Decode back to string and print... we should hit no exception here
  # since all errors have been replaced:
  print(codecs.getdecoder(sys.stdout.encoding)(bytes)[0])
  print()

def run(command, logFile):
  if cygwin: command = cygwinifyPaths(command)
  if os.system('%s > %s 2>&1' % (command, logFile)):
    logPath = os.path.abspath(logFile)
    print('\ncommand "%s" failed:' % command)
    printFileContents(logFile)
    raise RuntimeError('command "%s" failed; see log file %s' % (command, logPath))

def verifyDigests(artifact, urlString, tmpDir):
  print('    verify md5/sha1 digests')
  md5Expected, t = load(urlString + '.md5').strip().split()
  if t != '*'+artifact:
    raise RuntimeError('MD5 %s.md5 lists artifact %s but expected *%s' % (urlString, t, artifact))

  sha1Expected, t = load(urlString + '.sha1').strip().split()
  if t != '*'+artifact:
    raise RuntimeError('SHA1 %s.sha1 lists artifact %s but expected *%s' % (urlString, t, artifact))

  m = hashlib.md5()
  s = hashlib.sha1()
  f = open('%s/%s' % (tmpDir, artifact), 'rb')
  while True:
    x = f.read(65536)
    if len(x) == 0:
      break
    m.update(x)
    s.update(x)
  f.close()
  md5Actual = m.hexdigest()
  sha1Actual = s.hexdigest()
  if md5Actual != md5Expected:
    raise RuntimeError('MD5 digest mismatch for %s: expected %s but got %s' % (artifact, md5Expected, md5Actual))
  if sha1Actual != sha1Expected:
    raise RuntimeError('SHA1 digest mismatch for %s: expected %s but got %s' % (artifact, sha1Expected, sha1Actual))

def getDirEntries(urlString):
  if urlString.startswith('file:/') and not urlString.startswith('file://'):
    # stupid bogus ant URI
    urlString = "file:///" + urlString[6:]

  if urlString.startswith('file://'):
    path = urlString[7:]
    if path.endswith('/'):
      path = path[:-1]
    if cygwin: # Convert Windows path to Cygwin path
      path = re.sub(r'^/([A-Za-z]):/', r'/cygdrive/\1/', path)
    l = []
    for ent in os.listdir(path):
      entPath = '%s/%s' % (path, ent)
      if os.path.isdir(entPath):
        entPath += '/'
        ent += '/'
      l.append((ent, 'file://%s' % entPath))
    l.sort()
    return l
  else:
    links = getHREFs(urlString)
    for i, (text, subURL) in enumerate(links):
      if text == 'Parent Directory' or text == '..':
        return links[(i+1):]

def unpackAndVerify(java, project, tmpDir, artifact, svnRevision, version, testArgs, baseURL):
  destDir = '%s/unpack' % tmpDir
  if os.path.exists(destDir):
    shutil.rmtree(destDir)
  os.makedirs(destDir)
  os.chdir(destDir)
  print('  unpack %s...' % artifact)
  unpackLogFile = '%s/%s-unpack-%s.log' % (tmpDir, project, artifact)
  if artifact.endswith('.tar.gz') or artifact.endswith('.tgz'):
    run('tar xzf %s/%s' % (tmpDir, artifact), unpackLogFile)
  elif artifact.endswith('.zip'):
    run('unzip %s/%s' % (tmpDir, artifact), unpackLogFile)

  # make sure it unpacks to proper subdir
  l = os.listdir(destDir)
  expected = '%s-%s' % (project, version)
  if l != [expected]:
    raise RuntimeError('unpack produced entries %s; expected only %s' % (l, expected))

  unpackPath = '%s/%s' % (destDir, expected)
  verifyUnpacked(java, project, artifact, unpackPath, svnRevision, version, testArgs, tmpDir, baseURL)

LUCENE_NOTICE = None
LUCENE_LICENSE = None
SOLR_NOTICE = None
SOLR_LICENSE = None

def verifyUnpacked(java, project, artifact, unpackPath, svnRevision, version, testArgs, tmpDir, baseURL):
  global LUCENE_NOTICE
  global LUCENE_LICENSE
  global SOLR_NOTICE
  global SOLR_LICENSE

  os.chdir(unpackPath)
  isSrc = artifact.find('-src') != -1

  l = os.listdir(unpackPath)
  textFiles = ['LICENSE', 'NOTICE', 'README']
  if project == 'lucene':
    textFiles.extend(('JRE_VERSION_MIGRATION', 'CHANGES', 'MIGRATE', 'SYSTEM_REQUIREMENTS'))
    if isSrc:
      textFiles.append('BUILD')

  for fileName in textFiles:
    fileName += '.txt'
    if fileName not in l:
      raise RuntimeError('file "%s" is missing from artifact %s' % (fileName, artifact))
    l.remove(fileName)

  if project == 'lucene':
    if LUCENE_NOTICE is None:
      LUCENE_NOTICE = open('%s/NOTICE.txt' % unpackPath, encoding='UTF-8').read()
    if LUCENE_LICENSE is None:
      LUCENE_LICENSE = open('%s/LICENSE.txt' % unpackPath, encoding='UTF-8').read()
  else:
    if SOLR_NOTICE is None:
      SOLR_NOTICE = open('%s/NOTICE.txt' % unpackPath, encoding='UTF-8').read()
    if SOLR_LICENSE is None:
      SOLR_LICENSE = open('%s/LICENSE.txt' % unpackPath, encoding='UTF-8').read()

  if not isSrc:
    # TODO: we should add verifyModule/verifySubmodule (e.g. analysis) here and recurse through
    if project == 'lucene':
      expectedJARs = ()
    else:
      expectedJARs = ()

    for fileName in expectedJARs:
      fileName += '.jar'
      if fileName not in l:
        raise RuntimeError('%s: file "%s" is missing from artifact %s' % (project, fileName, artifact))
      l.remove(fileName)

  if project == 'lucene':
    # TODO: clean this up to not be a list of modules that we must maintain
    extras = ('analysis', 'backward-codecs', 'benchmark', 'classification', 'codecs', 'core', 'demo', 'docs', 'expressions', 'facet', 'grouping', 'highlighter', 'join', 'memory', 'misc', 'queries', 'queryparser', 'replicator', 'sandbox', 'spatial', 'suggest', 'test-framework', 'licenses')
    if isSrc:
      extras += ('build.xml', 'common-build.xml', 'module-build.xml', 'ivy-settings.xml', 'ivy-versions.properties', 'ivy-ignore-conflicts.properties', 'version.properties', 'tools', 'site')
  else:
    extras = ()

  # TODO: if solr, verify lucene/licenses, solr/licenses are present

  for e in extras:
    if e not in l:
      raise RuntimeError('%s: %s missing from artifact %s' % (project, e, artifact))
    l.remove(e)

  if project == 'lucene':
    if len(l) > 0:
      raise RuntimeError('%s: unexpected files/dirs in artifact %s: %s' % (project, artifact, l))

  if isSrc:
    print('    make sure no JARs/WARs in src dist...')
    lines = os.popen('find . -name \\*.jar').readlines()
    if len(lines) != 0:
      print('    FAILED:')
      for line in lines:
        print('      %s' % line.strip())
      raise RuntimeError('source release has JARs...')
    lines = os.popen('find . -name \\*.war').readlines()
    if len(lines) != 0:
      print('    FAILED:')
      for line in lines:
        print('      %s' % line.strip())
      raise RuntimeError('source release has WARs...')

    print('    run "ant validate"')
    java.run_java7('ant validate', '%s/validate.log' % unpackPath)

    if project == 'lucene':
      print("    run tests w/ Java 7 and testArgs='%s'..." % testArgs)
      java.run_java7('ant clean test %s' % testArgs, '%s/test.log' % unpackPath)
      java.run_java7('ant jar', '%s/compile.log' % unpackPath)
      testDemo(java.run_java7, isSrc, version, '1.7')

      print('    generate javadocs w/ Java 7...')
      java.run_java7('ant javadocs', '%s/javadocs.log' % unpackPath)
      checkJavadocpathFull('%s/build/docs' % unpackPath)

      if java.run_java8:
      print("    run tests w/ Java 8 and testArgs='%s'..." % testArgs)
      java.run_java8('ant clean test %s' % testArgs, '%s/test.log' % unpackPath)
      java.run_java8('ant jar', '%s/compile.log' % unpackPath)
      testDemo(java.run_java8, isSrc, version, '1.8')

      print('    generate javadocs w/ Java 8...')
      java.run_java8('ant javadocs', '%s/javadocs.log' % unpackPath)
      checkJavadocpathFull('%s/build/docs' % unpackPath)

    else:
      os.chdir('solr')

      print("    run tests w/ Java 7 and testArgs='%s'..." % testArgs)
      java.run_java7('ant clean test -Dtests.slow=false %s' % testArgs, '%s/test.log' % unpackPath)

      # test javadocs
      print('    generate javadocs w/ Java 7...')
      java.run_java7('ant clean javadocs', '%s/javadocs.log' % unpackPath)
      checkJavadocpathFull('%s/solr/build/docs' % unpackPath, False)

      print('    test solr example w/ Java 7...')
      java.run_java7('ant clean example', '%s/antexample.log' % unpackPath)
      testSolrExample(unpackPath, java.java7_home, True)

      if java.run_java8:
      print("    run tests w/ Java 8 and testArgs='%s'..." % testArgs)
      java.run_java8('ant clean test -Dtests.slow=false %s' % testArgs, '%s/test.log' % unpackPath)

      print('    generate javadocs w/ Java 8...')
      java.run_java8('ant clean javadocs', '%s/javadocs.log' % unpackPath)
      checkJavadocpathFull('%s/solr/build/docs' % unpackPath, False)

      print('    test solr example w/ Java 8...')
      java.run_java8('ant clean example', '%s/antexample.log' % unpackPath)
      testSolrExample(unpackPath, java.java8_home, True)

      os.chdir('..')
      print('    check NOTICE')
      testNotice(unpackPath)

  else:

    checkAllJARs(os.getcwd(), project, svnRevision, version, tmpDir, baseURL)

    if project == 'lucene':
      testDemo(java.run_java7, isSrc, version, '1.7')
      if java.run_java8:
      testDemo(java.run_java8, isSrc, version, '1.8')

      print('    check Lucene\'s javadoc JAR')
      checkJavadocpath('%s/docs' % unpackPath)

    else:
      checkSolrWAR('%s/server/webapps/solr.war' % unpackPath, svnRevision, version, tmpDir, baseURL)

      print('    copying unpacked distribution for Java 7 ...')
      java7UnpackPath = '%s-java7' % unpackPath
      if os.path.exists(java7UnpackPath):
        shutil.rmtree(java7UnpackPath)
      shutil.copytree(unpackPath, java7UnpackPath)
      os.chdir(java7UnpackPath)
      print('    test solr example w/ Java 7...')
      testSolrExample(java7UnpackPath, java.java7_home, False)

      if java.run_java8:
      print('    copying unpacked distribution for Java 8 ...')
      java8UnpackPath = '%s-java8' % unpackPath
      if os.path.exists(java8UnpackPath):
        shutil.rmtree(java8UnpackPath)
      shutil.copytree(unpackPath, java8UnpackPath)
      os.chdir(java8UnpackPath)
      print('    test solr example w/ Java 8...')
      testSolrExample(java8UnpackPath, java.java8_home, False)

      os.chdir(unpackPath)

  testChangesText('.', version, project)

  if project == 'lucene' and isSrc:
    print('  confirm all releases have coverage in TestBackwardsCompatibility')
    confirmAllReleasesAreTestedForBackCompat(unpackPath)


def testNotice(unpackPath):
  solrNotice = open('%s/NOTICE.txt' % unpackPath, encoding='UTF-8').read()
  luceneNotice = open('%s/lucene/NOTICE.txt' % unpackPath, encoding='UTF-8').read()

  expected = """
=========================================================================
==  Apache Lucene Notice                                               ==
=========================================================================

""" + luceneNotice + """---
"""

  if solrNotice.find(expected) == -1:
    raise RuntimeError('Solr\'s NOTICE.txt does not have the verbatim copy, plus header/footer, of Lucene\'s NOTICE.txt')

def readSolrOutput(p, startupEvent, failureEvent, logFile):
  f = open(logFile, 'wb')
  try:
    while True:
      line = p.stdout.readline()
      if len(line) == 0:
        p.poll()
        if not startupEvent.isSet():
          failureEvent.set()
          startupEvent.set()
        break
      f.write(line)
      f.flush()
      #print('SOLR: %s' % line.strip())
      if not startupEvent.isSet():
        if line.find(b'Started ServerConnector@') != -1 and line.find(b'{HTTP/1.1}{0.0.0.0:8983}') != -1:
          startupEvent.set()
        elif p.poll() is not None:
          failureEvent.set()
          startupEvent.set()
          break
  except:
    print()
    print('Exception reading Solr output:')
    traceback.print_exc()
    failureEvent.set()
    startupEvent.set()
  finally:
    f.close()

def testSolrExample(unpackPath, javaPath, isSrc):
  logFile = '%s/solr-example.log' % unpackPath
  if isSrc:
    os.chdir(unpackPath+'/solr')
    subprocess.call(['chmod','+x',unpackPath+'/solr/bin/solr'])
  else:
    os.chdir(unpackPath)

  print('      start Solr instance (log=%s)...' % logFile)
  env = {}
  env.update(os.environ)
  env['JAVA_HOME'] = javaPath
  env['PATH'] = '%s/bin:%s' % (javaPath, env['PATH'])

  # Stop Solr running on port 8983 (in case a previous run didn't shutdown cleanly)
  try:
    subprocess.call(['bin/solr','stop','-p','8983'])
  except:
    print('      Stop failed due to: '+sys.exc_info()[0])

  print('      starting Solr on port 8983 from %s' % unpackPath)
  server = subprocess.Popen(['bin/solr', '-f', '-p', '8983'], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, stdin=subprocess.PIPE, env=env)

  startupEvent = threading.Event()
  failureEvent = threading.Event()
  serverThread = threading.Thread(target=readSolrOutput, args=(server, startupEvent, failureEvent, logFile))
  serverThread.setDaemon(True)
  serverThread.start()

  try:

    # Make sure Solr finishes startup:
    if not startupEvent.wait(1800):
      raise RuntimeError('startup took more than 30 minutes')

    if failureEvent.isSet():
      logFile = os.path.abspath(logFile)
      print
      print('Startup failed; see log %s' % logFile)
      printFileContents(logFile)
      raise RuntimeError('failure on startup; see log %s' % logFile)

    print('      startup done')
    # Create the techproducts config (used to be collection1)
    subprocess.call(['bin/solr','create_core','-c','techproducts','-d','sample_techproducts_configs'])
    os.chdir('example')
    print('      test utf8...')
    run('sh ./exampledocs/test_utf8.sh http://localhost:8983/solr/techproducts', 'utf8.log')
    print('      index example docs...')
    run('java -Durl=http://localhost:8983/solr/techproducts/update -jar ./exampledocs/post.jar ./exampledocs/*.xml', 'post-example-docs.log')
    print('      run query...')
    s = load('http://localhost:8983/solr/techproducts/select/?q=video')
    if s.find('<result name="response" numFound="3" start="0">') == -1:
      print('FAILED: response is:\n%s' % s)
      raise RuntimeError('query on solr example instance failed')
  finally:
    # Stop server:
    print('      stop server using: bin/solr stop -p 8983')
    if isSrc:
      os.chdir(unpackPath+'/solr')
    else:
      os.chdir(unpackPath)
    subprocess.call(['bin/solr','stop','-p','8983'])

    # Give it 10 seconds to gracefully shut down
    serverThread.join(10.0)

    if serverThread.isAlive():
      # Kill server:
      print('***WARNING***: Solr instance didn\'t respond to SIGINT; using SIGKILL now...')
      os.kill(server.pid, signal.SIGKILL)

      serverThread.join(10.0)

      if serverThread.isAlive():
        # Shouldn't happen unless something is seriously wrong...
        print('***WARNING***: Solr instance didn\'t respond to SIGKILL; ignoring...')

  if failureEvent.isSet():
    raise RuntimeError('exception while reading Solr output')

  if isSrc:
    os.chdir(unpackPath+'/solr')
  else:
    os.chdir(unpackPath)

# the weaker check: we can use this on java6 for some checks,
# but its generated HTML is hopelessly broken so we cannot run
# the link checking that checkJavadocpathFull does.
def checkJavadocpath(path, failOnMissing=True):
  # check for level='package'
  # we fail here if its screwed up
  if failOnMissing and checkJavaDocs.checkPackageSummaries(path, 'package'):
    raise RuntimeError('missing javadocs package summaries!')

  # now check for level='class'
  if checkJavaDocs.checkPackageSummaries(path):
    # disabled: RM cannot fix all this, see LUCENE-3887
    # raise RuntimeError('javadoc problems')
    print('\n***WARNING***: javadocs want to fail!\n')

# full checks
def checkJavadocpathFull(path, failOnMissing=True):
  # check for missing, etc
  checkJavadocpath(path, failOnMissing)

  # also validate html/check for broken links
  if checkJavadocLinks.checkAll(path):
    raise RuntimeError('broken javadocs links found!')

def testDemo(run_java, isSrc, version, jdk):
  if os.path.exists('index'):
    shutil.rmtree('index') # nuke any index from any previous iteration

  print('    test demo with %s...' % jdk)
  sep = ';' if cygwin else ':'
  if isSrc:
    cp = 'build/core/classes/java{0}build/demo/classes/java{0}build/analysis/common/classes/java{0}build/queryparser/classes/java'.format(sep)
    docsDir = 'core/src'
  else:
    cp = 'core/lucene-core-{0}.jar{1}demo/lucene-demo-{0}.jar{1}analysis/common/lucene-analyzers-common-{0}.jar{1}queryparser/lucene-queryparser-{0}.jar'.format(version, sep)
    docsDir = 'docs'
  run_java('java -cp "%s" org.apache.lucene.demo.IndexFiles -index index -docs %s' % (cp, docsDir), 'index.log')
  run_java('java -cp "%s" org.apache.lucene.demo.SearchFiles -index index -query lucene' % cp, 'search.log')
  reMatchingDocs = re.compile('(\d+) total matching documents')
  m = reMatchingDocs.search(open('search.log', encoding='UTF-8').read())
  if m is None:
    raise RuntimeError('lucene demo\'s SearchFiles found no results')
  else:
    numHits = int(m.group(1))
    if numHits < 100:
      raise RuntimeError('lucene demo\'s SearchFiles found too few results: %s' % numHits)
    print('      got %d hits for query "lucene"' % numHits)
  print('    checkindex with %s...' % jdk)
  run_java('java -ea -cp "%s" org.apache.lucene.index.CheckIndex index' % cp, 'checkindex.log')
  s = open('checkindex.log').read()
  m = re.search(r'^\s+version=(.*?)$', s, re.MULTILINE)
  if m is None:
    raise RuntimeError('unable to locate version=NNN output from CheckIndex; see checkindex.log')
  actualVersion = m.group(1)
  if removeTrailingZeros(actualVersion) != removeTrailingZeros(version):
    raise RuntimeError('wrong version from CheckIndex: got "%s" but expected "%s"' % (actualVersion, version))

def removeTrailingZeros(version):
  return re.sub(r'(\.0)*$', '', version)

def checkMaven(baseURL, tmpDir, svnRevision, version, isSigned):
  # Locate the release branch in subversion
  m = re.match('(\d+)\.(\d+)', version) # Get Major.minor version components
  releaseBranchText = 'lucene_solr_%s_%s/' % (m.group(1), m.group(2))
  branchesURL = 'http://svn.apache.org/repos/asf/lucene/dev/branches/'
  releaseBranchSvnURL = None
  branches = getDirEntries(branchesURL)
  for text, subURL in branches:
    if text == releaseBranchText:
      releaseBranchSvnURL = subURL

  POMtemplates = defaultdict()
  getPOMtemplates(POMtemplates, tmpDir, releaseBranchSvnURL)
  print()
  print('    download artifacts')
  artifacts = {'lucene': [], 'solr': []}
  for project in ('lucene', 'solr'):
    artifactsURL = '%s/%s/maven/org/apache/%s/' % (baseURL, project, project)
    targetDir = '%s/maven/org/apache/%s' % (tmpDir, project)
    if not os.path.exists(targetDir):
      os.makedirs(targetDir)
    crawl(artifacts[project], artifactsURL, targetDir)
  print()
  verifyPOMperBinaryArtifact(artifacts, version)
  verifyArtifactPerPOMtemplate(POMtemplates, artifacts, tmpDir, version)
  verifyMavenDigests(artifacts)
  checkJavadocAndSourceArtifacts(artifacts, version)
  verifyDeployedPOMsCoordinates(artifacts, version)
  if isSigned:
    verifyMavenSigs(baseURL, tmpDir, artifacts)

  distFiles = getBinaryDistFilesForMavenChecks(tmpDir, version, baseURL)
  checkIdenticalMavenArtifacts(distFiles, artifacts, version)

  checkAllJARs('%s/maven/org/apache/lucene' % tmpDir, 'lucene', svnRevision, version, tmpDir, baseURL)
  checkAllJARs('%s/maven/org/apache/solr' % tmpDir, 'solr', svnRevision, version, tmpDir, baseURL)

def getBinaryDistFilesForMavenChecks(tmpDir, version, baseURL):
  distFiles = defaultdict()
  for project in ('lucene', 'solr'):
    distFiles[project] = getBinaryDistFiles(project, tmpDir, version, baseURL)
  return distFiles

def getBinaryDistFiles(project, tmpDir, version, baseURL):
  distribution = '%s-%s.tgz' % (project, version)
  if not os.path.exists('%s/%s' % (tmpDir, distribution)):
    distURL = '%s/%s/%s' % (baseURL, project, distribution)
    print('    download %s...' % distribution, end=' ')
    download(distribution, distURL, tmpDir)
  destDir = '%s/unpack-%s-getBinaryDistFiles' % (tmpDir, project)
  if os.path.exists(destDir):
    shutil.rmtree(destDir)
  os.makedirs(destDir)
  os.chdir(destDir)
  print('    unpack %s...' % distribution)
  unpackLogFile = '%s/unpack-%s-getBinaryDistFiles.log' % (tmpDir, distribution)
  run('tar xzf %s/%s' % (tmpDir, distribution), unpackLogFile)
  distributionFiles = []
  for root, dirs, files in os.walk(destDir):
    distributionFiles.extend([os.path.join(root, file) for file in files])
  return distributionFiles

def checkJavadocAndSourceArtifacts(artifacts, version):
  print('    check for javadoc and sources artifacts...')
  for project in ('lucene', 'solr'):
    for artifact in artifacts[project]:
      if artifact.endswith(version + '.jar'):
        javadocJar = artifact[:-4] + '-javadoc.jar'
        if javadocJar not in artifacts[project]:
          raise RuntimeError('missing: %s' % javadocJar)
        sourcesJar = artifact[:-4] + '-sources.jar'
        if sourcesJar not in artifacts[project]:
          raise RuntimeError('missing: %s' % sourcesJar)

def getZipFileEntries(fileName):
  entries = []
  with zipfile.ZipFile(fileName) as zf:
    for zi in zf.infolist():
      entries.append(zi.filename)
  # Sort by name:
  entries.sort()
  return entries

def checkIdenticalMavenArtifacts(distFiles, artifacts, version):
  print('    verify that Maven artifacts are same as in the binary distribution...')
  reJarWar = re.compile(r'%s\.[wj]ar$' % version) # exclude *-javadoc.jar and *-sources.jar
  for project in ('lucene', 'solr'):
    distFilenames = dict()
    for file in distFiles[project]:
      baseName = os.path.basename(file)
      distFilenames[baseName] = file
    for artifact in artifacts[project]:
      if reJarWar.search(artifact):
        artifactFilename = os.path.basename(artifact)
        if artifactFilename not in distFilenames:
          raise RuntimeError('Maven artifact %s is not present in %s binary distribution'
                             % (artifact, project))
        else:
          identical = filecmp.cmp(artifact, distFilenames[artifactFilename], shallow=False)
          if not identical:
            raise RuntimeError('Maven artifact %s is not identical to %s in %s binary distribution'
                               % (artifact, distFilenames[artifactFilename], project))

def verifyMavenDigests(artifacts):
  print("    verify Maven artifacts' md5/sha1 digests...")
  reJarWarPom = re.compile(r'\.(?:[wj]ar|pom)$')
  for project in ('lucene', 'solr'):
    for artifactFile in [a for a in artifacts[project] if reJarWarPom.search(a)]:
      if artifactFile + '.md5' not in artifacts[project]:
        raise RuntimeError('missing: MD5 digest for %s' % artifactFile)
      if artifactFile + '.sha1' not in artifacts[project]:
        raise RuntimeError('missing: SHA1 digest for %s' % artifactFile)
      with open(artifactFile + '.md5', encoding='UTF-8') as md5File:
        md5Expected = md5File.read().strip()
      with open(artifactFile + '.sha1', encoding='UTF-8') as sha1File:
        sha1Expected = sha1File.read().strip()
      md5 = hashlib.md5()
      sha1 = hashlib.sha1()
      inputFile = open(artifactFile, 'rb')
      while True:
        bytes = inputFile.read(65536)
        if len(bytes) == 0:
          break
        md5.update(bytes)
        sha1.update(bytes)
      inputFile.close()
      md5Actual = md5.hexdigest()
      sha1Actual = sha1.hexdigest()
      if md5Actual != md5Expected:
        raise RuntimeError('MD5 digest mismatch for %s: expected %s but got %s'
                           % (artifactFile, md5Expected, md5Actual))
      if sha1Actual != sha1Expected:
        raise RuntimeError('SHA1 digest mismatch for %s: expected %s but got %s'
                           % (artifactFile, sha1Expected, sha1Actual))

def getPOMcoordinate(treeRoot):
  namespace = '{http://maven.apache.org/POM/4.0.0}'
  groupId = treeRoot.find('%sgroupId' % namespace)
  if groupId is None:
    groupId = treeRoot.find('{0}parent/{0}groupId'.format(namespace))
  groupId = groupId.text.strip()
  artifactId = treeRoot.find('%sartifactId' % namespace).text.strip()
  version = treeRoot.find('%sversion' % namespace)
  if version is None:
    version = treeRoot.find('{0}parent/{0}version'.format(namespace))
  version = version.text.strip()
  packaging = treeRoot.find('%spackaging' % namespace)
  packaging = 'jar' if packaging is None else packaging.text.strip()
  return groupId, artifactId, packaging, version

def verifyMavenSigs(baseURL, tmpDir, artifacts):
  print('    verify maven artifact sigs', end=' ')
  for project in ('lucene', 'solr'):
    keysFile = '%s/%s.KEYS' % (tmpDir, project)
    if not os.path.exists(keysFile):
      keysURL = '%s/%s/KEYS' % (baseURL, project)
      download('%s.KEYS' % project, keysURL, tmpDir, quiet=True)

    # Set up clean gpg world; import keys file:
    gpgHomeDir = '%s/%s.gpg' % (tmpDir, project)
    if os.path.exists(gpgHomeDir):
      shutil.rmtree(gpgHomeDir)
    os.makedirs(gpgHomeDir, 0o700)
    run('gpg --homedir %s --import %s' % (gpgHomeDir, keysFile),
        '%s/%s.gpg.import.log' % (tmpDir, project))

    reArtifacts = re.compile(r'\.(?:pom|[jw]ar)$')
    for artifactFile in [a for a in artifacts[project] if reArtifacts.search(a)]:
      artifact = os.path.basename(artifactFile)
      sigFile = '%s.asc' % artifactFile
      # Test sig (this is done with a clean brand-new GPG world)
      logFile = '%s/%s.%s.gpg.verify.log' % (tmpDir, project, artifact)
      run('gpg --homedir %s --verify %s %s' % (gpgHomeDir, sigFile, artifactFile),
          logFile)
      # Forward any GPG warnings, except the expected one (since it's a clean world)
      f = open(logFile, encoding='UTF-8')
      for line in f.readlines():
        if line.lower().find('warning') != -1 \
                and line.find('WARNING: This key is not certified with a trusted signature') == -1 \
                and line.find('WARNING: using insecure memory') == -1:
          print('      GPG: %s' % line.strip())
      f.close()

      # Test trust (this is done with the real users config)
      run('gpg --import %s' % keysFile,
          '%s/%s.gpg.trust.import.log' % (tmpDir, project))
      logFile = '%s/%s.%s.gpg.trust.log' % (tmpDir, project, artifact)
      run('gpg --verify %s %s' % (sigFile, artifactFile), logFile)
      # Forward any GPG warnings:
      f = open(logFile, encoding='UTF-8')
      for line in f.readlines():
        if line.lower().find('warning') != -1 \
                and line.find('WARNING: This key is not certified with a trusted signature') == -1 \
                and line.find('WARNING: using insecure memory') == -1:
          print('      GPG: %s' % line.strip())
      f.close()

      sys.stdout.write('.')
  print()

def verifyPOMperBinaryArtifact(artifacts, version):
  print('    verify that each binary artifact has a deployed POM...')
  reBinaryJarWar = re.compile(r'%s\.[jw]ar$' % re.escape(version))
  for project in ('lucene', 'solr'):
    for artifact in [a for a in artifacts[project] if reBinaryJarWar.search(a)]:
      POM = artifact[:-4] + '.pom'
      if POM not in artifacts[project]:
        raise RuntimeError('missing: POM for %s' % artifact)

def verifyDeployedPOMsCoordinates(artifacts, version):
  """
  verify that each POM's coordinate (drawn from its content) matches
  its filepath, and verify that the corresponding artifact exists.
  """
  print("    verify deployed POMs' coordinates...")
  for project in ('lucene', 'solr'):
    for POM in [a for a in artifacts[project] if a.endswith('.pom')]:
      treeRoot = ET.parse(POM).getroot()
      groupId, artifactId, packaging, POMversion = getPOMcoordinate(treeRoot)
      POMpath = '%s/%s/%s/%s-%s.pom' \
                % (groupId.replace('.', '/'), artifactId, version, artifactId, version)
      if not POM.endswith(POMpath):
        raise RuntimeError("Mismatch between POM coordinate %s:%s:%s and filepath: %s"
                           % (groupId, artifactId, POMversion, POM))
      # Verify that the corresponding artifact exists
      artifact = POM[:-3] + packaging
      if artifact not in artifacts[project]:
        raise RuntimeError('Missing corresponding .%s artifact for POM %s' % (packaging, POM))

def verifyArtifactPerPOMtemplate(POMtemplates, artifacts, tmpDir, version):
  print('    verify that there is an artifact for each POM template...')
  namespace = '{http://maven.apache.org/POM/4.0.0}'
  xpathPlugin = '{0}build/{0}plugins/{0}plugin'.format(namespace)
  xpathSkipConfiguration = '{0}configuration/{0}skip'.format(namespace)
  for project in ('lucene', 'solr'):
    for POMtemplate in POMtemplates[project]:
      treeRoot = ET.parse(POMtemplate).getroot()
      skipDeploy = False
      for plugin in treeRoot.findall(xpathPlugin):
        artifactId = plugin.find('%sartifactId' % namespace).text.strip()
        if artifactId == 'maven-deploy-plugin':
          skip = plugin.find(xpathSkipConfiguration)
          if skip is not None: skipDeploy = (skip.text.strip().lower() == 'true')
      if not skipDeploy:
        groupId, artifactId, packaging, POMversion = getPOMcoordinate(treeRoot)
        # Ignore POMversion, since its value will not have been interpolated
        artifact = '%s/maven/%s/%s/%s/%s-%s.%s' \
                   % (tmpDir, groupId.replace('.', '/'), artifactId,
                      version, artifactId, version, packaging)
        if artifact not in artifacts['lucene'] and artifact not in artifacts['solr']:
          raise RuntimeError('Missing artifact %s' % artifact)

def getPOMtemplates(POMtemplates, tmpDir, releaseBranchSvnURL):
  print('    get POM templates')
  allPOMtemplates = []
  sourceLocation = releaseBranchSvnURL
  if sourceLocation is None:
    # Use the POM templates under dev-tools/maven/ in the local working copy
    # sys.path[0] is the directory containing this script: dev-tools/scripts/
    sourceLocation = os.path.abspath('%s/../maven' % sys.path[0])
    rePOMtemplate = re.compile(r'^pom.xml.template$')
    for root, dirs, files in os.walk(sourceLocation):
      allPOMtemplates.extend([os.path.join(root, f) for f in files if rePOMtemplate.search(f)])
  else:
    sourceLocation += 'dev-tools/maven/'
    targetDir = '%s/dev-tools/maven' % tmpDir
    if not os.path.exists(targetDir):
      os.makedirs(targetDir)
    crawl(allPOMtemplates, sourceLocation, targetDir, set(['Apache Subversion', 'maven.testlogging.properties']))

  reLucenePOMtemplate = re.compile(r'.*/maven/lucene.*/pom\.xml\.template$')
  POMtemplates['lucene'] = [p for p in allPOMtemplates if reLucenePOMtemplate.search(p)]
  if POMtemplates['lucene'] is None:
    raise RuntimeError('No Lucene POMs found at %s' % sourceLocation)
  reSolrPOMtemplate = re.compile(r'.*/maven/solr.*/pom\.xml\.template$')
  POMtemplates['solr'] = [p for p in allPOMtemplates if reSolrPOMtemplate.search(p)]
  if POMtemplates['solr'] is None:
    raise RuntimeError('No Solr POMs found at %s' % sourceLocation)
  POMtemplates['grandfather'] = [p for p in allPOMtemplates if '/maven/pom.xml.template' in p]
  if len(POMtemplates['grandfather']) == 0:
    raise RuntimeError('No Lucene/Solr grandfather POM found at %s' % sourceLocation)

def crawl(downloadedFiles, urlString, targetDir, exclusions=set()):
  for text, subURL in getDirEntries(urlString):
    if text not in exclusions:
      path = os.path.join(targetDir, text)
      if text.endswith('/'):
        if not os.path.exists(path):
          os.makedirs(path)
        crawl(downloadedFiles, subURL, path, exclusions)
      else:
        if not os.path.exists(path) or FORCE_CLEAN:
          download(text, subURL, targetDir, quiet=True)
        downloadedFiles.append(path)
        sys.stdout.write('.')

def make_java_config(parser, java8_home):
  def _make_runner(java_home, version):
    print('Java %s JAVA_HOME=%s' % (version, java_home))
    if cygwin:
      java_home = subprocess.check_output('cygpath -u "%s"' % java_home).read().decode('utf-8').strip()
    cmd_prefix = 'export JAVA_HOME="%s" PATH="%s/bin:$PATH" JAVACMD="%s/bin/java"' % \
                 (java_home, java_home, java_home)
    s = subprocess.check_output('%s; java -version' % cmd_prefix,
                                shell=True, stderr=subprocess.STDOUT).decode('utf-8')
    if s.find(' version "%s.' % version) == -1:
      parser.error('got wrong version for java %s:\n%s' % (version, s))
    def run_java(cmd, logfile):
      run('%s; %s' % (cmd_prefix, cmd), logfile)
    return run_java
  java7_home =  os.environ.get('JAVA_HOME')
  if java7_home is None:
    parser.error('JAVA_HOME must be set')
  run_java7 = _make_runner(java7_home, '1.7')
  run_java8 = None
  if java8_home is not None:
  run_java8 = _make_runner(java8_home, '1.8')

  jc = namedtuple('JavaConfig', 'run_java7 java7_home run_java8 java8_home')
  return jc(run_java7, java7_home, run_java8, java8_home)

version_re = re.compile(r'(\d+\.\d+\.\d+(-ALPHA|-BETA)?)')
revision_re = re.compile(r'rev(\d+)')
def parse_config():
  epilogue = textwrap.dedent('''
    Example usage:
    python3.2 -u dev-tools/scripts/smokeTestRelease.py http://people.apache.org/~whoever/staging_area/lucene-solr-4.3.0-RC1-rev1469340
  ''')
  description = 'Utility to test a release.'
  parser = argparse.ArgumentParser(description=description, epilog=epilogue,
                                   formatter_class=argparse.RawDescriptionHelpFormatter)
  parser.add_argument('--tmp-dir', metavar='PATH',
                      help='Temporary directory to test inside, defaults to /tmp/smoke_lucene_$version_$revision')
  parser.add_argument('--not-signed', dest='is_signed', action='store_false', default=True,
                      help='Indicates the release is not signed')
  parser.add_argument('--revision',
                      help='SVN revision number that release was built with, defaults to that in URL')
  parser.add_argument('--version', metavar='X.Y.Z(-ALPHA|-BETA)?',
                      help='Version of the release, defaults to that in URL')
  parser.add_argument('--test-java8', metavar='JAVA8_HOME',
                      help='Path to Java8 home directory, to run tests with if specified')
  parser.add_argument('url', help='Url pointing to release to test')
  parser.add_argument('test_args', nargs=argparse.REMAINDER,
                      help='Arguments to pass to ant for testing, e.g. -Dwhat=ever.')
  c = parser.parse_args()

  if c.version is not None:
    if not version_re.match(c.version):
      parser.error('version "%s" does not match format X.Y.Z[-ALPHA|-BETA]' % c.version)
  else:
    version_match = version_re.search(c.url)
    if version_match is None:
      parser.error('Could not find version in URL')
    c.version = version_match.group(1)

  if c.revision is None:
    revision_match = revision_re.search(c.url)
    if revision_match is None:
      parser.error('Could not find revision in URL')
    c.revision = revision_match.group(1)

  c.java = make_java_config(parser, c.test_java8)

  if c.tmp_dir:
    c.tmp_dir = os.path.abspath(c.tmp_dir)
  else:
    tmp = '/tmp/smoke_lucene_%s_%s' % (c.version, c.revision)
    c.tmp_dir = tmp
    i = 1
    while os.path.exists(c.tmp_dir):
      c.tmp_dir = tmp + '_%d' % i
      i += 1

  return c

reVersion1 = re.compile(r'\>(\d+)\.(\d+)\.(\d+)(-alpha|-beta)?/\<', re.IGNORECASE)
reVersion2 = re.compile(r'-(\d+)\.(\d+)\.(\d+)(-alpha|-beta)?\.', re.IGNORECASE)

def getAllLuceneReleases():
  s = load('https://archive.apache.org/dist/lucene/java')

  releases = set()
  for r in reVersion1, reVersion2:
    for tup in r.findall(s):
      if tup[-1].lower() == '-alpha':
        tup = tup[:3] + ('0',)
      elif tup[-1].lower() == '-beta':
        tup = tup[:3] + ('1',)
      elif tup[-1] == '':
        tup = tup[:3]
      else:
        raise RuntimeError('failed to parse version: %s' % tup[-1])
      releases.add(tuple(int(x) for x in tup))

  l = list(releases)
  l.sort()
  return l

def confirmAllReleasesAreTestedForBackCompat(unpackPath):

  print('    find all past Lucene releases...')
  allReleases = getAllLuceneReleases()
  #for tup in allReleases:
  #  print('  %s' % '.'.join(str(x) for x in tup))

  testedIndices = set()

  os.chdir(unpackPath)

  for suffix in '',:
    print('    run TestBackwardsCompatibility%s..' % suffix)
    command = 'ant test -Dtestcase=TestBackwardsCompatibility%s -Dtests.verbose=true' % suffix
    p = subprocess.Popen(command, shell=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    stdout, stderr = p.communicate()
    if p.returncode is not 0:
      # Not good: the test failed!
      raise RuntimeError('%s failed:\n%s' % (command, stdout))
    stdout = stdout.decode('utf-8')

    if stderr is not None:
      # Should not happen since we redirected stderr to stdout:
      raise RuntimeError('stderr non-empty')

    reIndexName = re.compile(r'TEST: (old index |index |\s*=\s*)(.*?)(-cfs|-nocfs)$', re.MULTILINE)
    for skip, name, cfsPart in reIndexName.findall(stdout):
      # Fragile: decode the inconsistent naming schemes we've used in TestBWC's indices:
      #print('parse name %s' % name)
      tup = tuple(name.split('.'))
      if len(tup) == 3:
        # ok
        tup = tuple(int(x) for x in tup)
      elif tup == ('4', '0', '0', '1'):
        # CONFUSING: this is the 4.0.0-alpha index??
        tup = 4, 0, 0, 0
      elif tup == ('4', '0', '0', '2'):
        # CONFUSING: this is the 4.0.0-beta index??
        tup = 4, 0, 0, 1
      else:
        raise RuntimeError('could not parse version %s' % name)

      testedIndices.add(tup)

  l = list(testedIndices)
  l.sort()
  if False:
    for release in l:
      print('  %s' % '.'.join(str(x) for x in release))

  allReleases = set(allReleases)

  for x in testedIndices:
    if x not in allReleases:
      # Curious: we test 1.9.0 index but it's not in the releases (I think it was pulled because of nasty bug?)
      if x != (1, 9, 0):
        raise RuntimeError('tested version=%s but it was not released?' % '.'.join(str(y) for y in x))

  notTested = []
  for x in allReleases:
    if x not in testedIndices:
      if '.'.join(str(y) for y in x) in ('1.4.3', '1.9.1', '2.3.1', '2.3.2'):
        # Exempt the dark ages indices
        continue
      notTested.append(x)

  if len(notTested) > 0:
    notTested.sort()
    print('Releases that don\'t seem to be tested:')
    failed = True
    for x in notTested:
      print('  %s' % '.'.join(str(y) for y in x))
    raise RuntimeError('some releases are not tested by TestBackwardsCompatibility?')
  else:
    print('    success!')

def main():
  c = parse_config()
  print('NOTE: output encoding is %s' % sys.stdout.encoding)
  smokeTest(c.java, c.url, c.revision, c.version, c.tmp_dir, c.is_signed, ' '.join(c.test_args))

def smokeTest(java, baseURL, svnRevision, version, tmpDir, isSigned, testArgs):

  startTime = datetime.datetime.now()

  if FORCE_CLEAN:
    if os.path.exists(tmpDir):
      raise RuntimeError('temp dir %s exists; please remove first' % tmpDir)

  if not os.path.exists(tmpDir):
    os.makedirs(tmpDir)

  lucenePath = None
  solrPath = None
  print()
  print('Load release URL "%s"...' % baseURL)
  newBaseURL = unshortenURL(baseURL)
  if newBaseURL != baseURL:
    print('  unshortened: %s' % newBaseURL)
    baseURL = newBaseURL

  for text, subURL in getDirEntries(baseURL):
    if text.lower().find('lucene') != -1:
      lucenePath = subURL
    elif text.lower().find('solr') != -1:
      solrPath = subURL

  if lucenePath is None:
    raise RuntimeError('could not find lucene subdir')
  if solrPath is None:
    raise RuntimeError('could not find solr subdir')

  print()
  print('Test Lucene...')
  checkSigs('lucene', lucenePath, version, tmpDir, isSigned)
  for artifact in ('lucene-%s.tgz' % version, 'lucene-%s.zip' % version):
    unpackAndVerify(java, 'lucene', tmpDir, artifact, svnRevision, version, testArgs, baseURL)
  unpackAndVerify(java, 'lucene', tmpDir, 'lucene-%s-src.tgz' % version, svnRevision, version, testArgs, baseURL)

  print()
  print('Test Solr...')
  checkSigs('solr', solrPath, version, tmpDir, isSigned)
  for artifact in ('solr-%s.tgz' % version, 'solr-%s.zip' % version):
    unpackAndVerify(java, 'solr', tmpDir, artifact, svnRevision, version, testArgs, baseURL)
  unpackAndVerify(java, 'solr', tmpDir, 'solr-%s-src.tgz' % version, svnRevision, version, testArgs, baseURL)

  print()
  print('Test Maven artifacts for Lucene and Solr...')
  checkMaven(baseURL, tmpDir, svnRevision, version, isSigned)

  print('\nSUCCESS! [%s]\n' % (datetime.datetime.now() - startTime))

if __name__ == '__main__':
  try:
    main()
  except KeyboardInterrupt:
    print('Keyboard interrupt...exiting')

