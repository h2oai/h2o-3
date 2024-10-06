package water.api;

import org.joda.time.DateTimeZone;
import water.H2O;
import water.H2ONode;
import water.H2OSecurityManager;
import water.Paxos;
import water.api.schemas3.CloudV3;
import water.parser.ParseTime;
import water.util.PrettyPrint;

import java.util.Date;

class CloudHandler extends Handler {
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public CloudV3 head(int version, CloudV3 cloud) {
    return cloud;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public CloudV3 status(int version, CloudV3 cloud) {
    // TODO: this really ought to be in the water package
    cloud.version = H2O.ABV.projectVersion();
    cloud.branch_name = H2O.ABV.branchName();
    cloud.last_commit_hash = H2O.ABV.lastCommitHash();
    cloud.describe = H2O.ABV.describe();
    cloud.compiled_by = H2O.ABV.compiledBy();
    cloud.compiled_on = H2O.ABV.compiledOn();
    cloud.build_number = H2O.ABV.buildNumber();
    cloud.build_age = PrettyPrint.toAge(H2O.ABV.compiledOnDate(), new Date());
    cloud.build_too_old = H2O.ABV.isTooOld();

    cloud.node_idx = H2O.SELF.index();
    cloud.cloud_name = H2O.ARGS.name;
    cloud.is_client  = H2O.ARGS.client;
    cloud.cloud_size = H2O.CLOUD.size();
    cloud.cloud_uptime_millis = System.currentTimeMillis() - H2O.START_TIME_MILLIS.get();
    cloud.cloud_internal_timezone = DateTimeZone.getDefault().toString();
    cloud.datafile_parser_timezone = ParseTime.getTimezone().toString();
    cloud.consensus = Paxos._commonKnowledge;
    cloud.locked = Paxos._cloudLocked;
    cloud.internal_security_enabled = H2OSecurityManager.instance().securityEnabled;
    cloud.web_ip = H2O.ARGS.web_ip;

    // set leader
    H2ONode leader = H2O.CLOUD.leaderOrNull(); // leader might be null in client mode if clouding didn't finish yet
    cloud.leader_idx = leader == null ? -1 : leader.index();

    // set list of members (might be empty)
    H2ONode[] members = H2O.CLOUD.members();
    cloud.bad_nodes = 0;
    cloud.cloud_healthy = true;
    cloud.nodes = new CloudV3.NodeV3[members.length];
    for (int i = 0; i < members.length; i++) {
      cloud.nodes[i] = new CloudV3.NodeV3(members[i], cloud.skip_ticks);
      if (! cloud.nodes[i].healthy) {
        cloud.cloud_healthy = false;
        cloud.bad_nodes++;
      }
    }

    return cloud;
  }
}

