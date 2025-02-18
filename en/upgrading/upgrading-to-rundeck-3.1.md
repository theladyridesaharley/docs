% Upgrading to Rundeck 3.1
% Greg Schueler
% May 21, 2019

## RPM package

> **Note:** There is now only a single RPM package required (rundeck-config is no more)

**Updating**  
For convenience the `3.1.0` rpm package obsoletes `rundeck-config`. When rundeck is updated
it will remove this package and take over the files without prompting. 

**Downgrading**  
If you need to downgrade and/or install a specific version of Rundeck prior to `3.1.0`:
```
yum --setopt=obsoletes=0 downgrade rundeck-3.0.24.20190719-1.201907192053
```

Otherwise there should be no problem upgrading from Rundeck 3.0 to Rundeck 3.1

If you are upgrading from an older version, please review the Upgrade Guide for the specific version.

## Docker using OpenShift

Some changes to the Docker image were added to support OpenShift, see [#4826](https://github.com/rundeck/rundeck/pull/4826).

* The `rundeck` user's default group needs to be `root(0)`
* Any files and directories Rundeck uses need to have the appropriate `root` group and permissions set
* Use `chown=rundeck:root` in Dockerfile with `ADD` and `COPY`
* Use `chmod 0775` on directories and files as appropriate
