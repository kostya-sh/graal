local vm = import '../ci_includes/vm.jsonnet';
local graal_common = import '../../../ci/ci_common/common.jsonnet';
local repo_config = import '../../../ci/repo-configuration.libsonnet';
local devkits = graal_common.devkits;
local c = import 'common.jsonnet';
local g = vm.compiler_gate;
local utils = import '../../../ci/ci_common/common-utils.libsonnet';

{
  local underscore(s) = std.strReplace(s, "-", "_"),
  local os(os_arch) = std.split(os_arch, "-")[0],
  local arch(os_arch) = std.split(os_arch, "-")[1],
  local t(limit) = {timelimit: limit},

  libgraal_build(build_args):: {
    local build_command = if repo_config.graalvm_edition == 'ce' then 'build' else 'build-libgraal-pgo',
    run+: [
      ['mx', '--env', vm.libgraal_env] + ['--extra-image-builder-argument=%s' % arg for arg in build_args] + [build_command]
    ]
  },

  # enable asserts in the JVM building the image and enable asserts in the resulting native image
  libgraal_compiler_base(quickbuild_args=[], extra_vm_args=[]):: self.libgraal_build(['-J-esa', '-J-ea', '-esa', '-ea'] + quickbuild_args) + {
    run+: [
      ['mx', '--env', vm.libgraal_env, 'gate', '--task', 'LibGraal Compiler', '--extra-vm-argument=' + std.join(" ", extra_vm_args)],
    ],
    logs+: [
      '*/graal-compiler.log',
      '*/graal-compiler-ctw.log'
    ],
    timelimit: '1:00:00',
  },

  # enable asserts in the JVM building the image and enable asserts in the resulting native image
  libgraal_compiler:: self.libgraal_compiler_base(),
  libgraal_compiler_zgc:: self.libgraal_compiler_base(extra_vm_args=['-XX:+UseZGC']),
  # enable economy mode building with the -Ob flag
  libgraal_compiler_quickbuild:: self.libgraal_compiler_base(quickbuild_args=['-Ob']),

  libgraal_truffle_base(quickbuild_args=[], extra_vm_args=[], coverage=false): self.libgraal_build(['-J-esa', '-J-ea', '-esa', '-ea'] + quickbuild_args) + {
    environment+: {
      # The Truffle TCK tests run as a part of Truffle TCK gate, tools tests run as a part of tools gate
      TEST_LIBGRAAL_EXCLUDE: 'com.oracle.truffle.tck.tests.* com.oracle.truffle.tools.*'
    },
    run+: [
      ['mx', '--env', vm.libgraal_env, 'gate', '--task', 'LibGraal Truffle'] + if coverage then g.jacoco_gate_args else [] +
        if extra_vm_args != [] then ['--extra-vm-argument=' + std.join(" ", extra_vm_args)] else [],
    ],
    logs+: [
      '*/graal-compiler.log',
      '*/graal-compiler-ctw.log'
    ],
    timelimit: '1:00:00',
    teardown+: if coverage then [
      g.upload_coverage
    ] else []
  },

  # -ea assertions are enough to keep execution time reasonable
  libgraal_truffle: self.libgraal_truffle_base(),
  libgraal_truffle_zgc: self.libgraal_truffle_base(extra_vm_args=['-XX:+UseZGC']),
  # enable economy mode building with the -Ob flag
  libgraal_truffle_quickbuild: self.libgraal_truffle_base(['-Ob']),

  # Use economy mode for coverage testing
  libgraal_truffle_coverage: self.libgraal_truffle_base(['-Ob'], coverage=true),

  # See definition of `gates` local variable in ../../compiler/ci_common/gate.jsonnet
  local gates = {
    "gate-vm-libgraal_compiler-labsjdk-20-linux-amd64": {},
    "gate-vm-libgraal_truffle-labsjdk-20-linux-amd64": {},
    "gate-vm-libgraal_compiler_zgc-labsjdk-20-linux-amd64": {},
    "gate-vm-libgraal_compiler_quickbuild-labsjdk-20-linux-amd64": {},
    "gate-vm-libgraal_truffle_quickbuild-labsjdk-20-linux-amd64": t("1:10:00"),
  },

  # See definition of `dailies` local variable in ../../compiler/ci_common/gate.jsonnet
  local dailies = {
    "daily-vm-libgraal_compiler-labsjdk-17-linux-amd64": {},
    "daily-vm-libgraal_truffle-labsjdk-17-linux-amd64": {},
    "daily-vm-libgraal_compiler_zgc-labsjdk-17-linux-amd64": {},
    "daily-vm-libgraal_truffle_zgc-labsjdk-17-linux-amd64": {},
    "daily-vm-libgraal_truffle_zgc-labsjdk-20-linux-amd64": {},
    "daily-vm-libgraal_compiler_quickbuild-labsjdk-17-linux-amd64": {},
    "daily-vm-libgraal_truffle_quickbuild-labsjdk-17-linux-amd64": {},
  },

  # See definition of `weeklies` local variable in ../../compiler/ci_common/gate.jsonnet
  local weeklies = {
    "weekly-vm-libgraal_truffle_coverage*": {}
  },

  # See definition of `monthlies` local variable in ../../compiler/ci_common/gate.jsonnet
  local monthlies = {},

  local svm_common(os_arch, jdk) =
    local obj = c["svm_common_" + underscore(os_arch)];
    if std.type(obj) == "function" then obj(jdk) else obj,

  # Builds run on all platforms (platform = JDK + OS + ARCH)
  local all_platforms_builds = [
    c["gate_vm_" + underscore(os_arch)] +
    svm_common(os_arch, jdk) +
    vm["custom_vm_" + os(os_arch)] +
    g.make_build(jdk, os_arch, task, extra_tasks=self, suite="vm",
                 include_common_os_arch=false,
                 gates_manifest=gates,
                 dailies_manifest=dailies,
                 weeklies_manifest=weeklies,
                 monthlies_manifest=monthlies).build +
    vm["vm_java_" + jdk]
    for jdk in [
      "17",
      "20"
    ]
    for os_arch in [
      "linux-amd64",
      "linux-aarch64",
      "darwin-amd64",
      "darwin-aarch64"
    ]
    for task in [
      "libgraal_compiler",
      "libgraal_truffle",
      "libgraal_compiler_zgc",
      "libgraal_truffle_zgc",
      "libgraal_compiler_quickbuild",
      "libgraal_truffle_quickbuild"
    ]
  ],


  # Builds run on only on linux-amd64-jdk20
  local linux_amd64_jdk20_builds = [
    c["gate_vm_" + underscore(os_arch)] +
    svm_common(os_arch, jdk) +
    vm["custom_vm_" + os(os_arch)] +
    g.make_build(jdk, os_arch, task, extra_tasks=self, suite="vm",
                 include_common_os_arch=false,
                 gates_manifest=gates,
                 dailies_manifest=dailies,
                 weeklies_manifest=weeklies,
                 monthlies_manifest=monthlies).build +
    vm["vm_java_" + jdk]
    for jdk in [
      "20"
    ]
    for os_arch in [
      "linux-amd64",
      "darwin-aarch64",
      "windows-amd64"
    ]
    for task in [
      "libgraal_truffle_coverage"
    ]
  ],

  # Complete set of builds defined in this file
  local all_builds =
    all_platforms_builds +
    linux_amd64_jdk20_builds,

  builds: if
      g.check_manifest(gates, all_builds, std.thisFile, "gates").result
    then
      local conf = repo_config.vm.libgraal_predicate_conf;
      [utils.add_gate_predicate(b, suites=conf.suites, extra_excludes=conf.extra_excludes) for b in all_builds]
}
