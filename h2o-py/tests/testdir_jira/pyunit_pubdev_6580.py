import sys
sys.path.insert(1,"../../")
from tests import pyunit_utils
from h2o.backend.server import H2OLocalServer
from h2o.exceptions import H2OStartupError
import unittest


class TestUnsupportedJavaVersionCheck(unittest.TestCase):
    def test_numeric_response_error(self):
        
        ######  Versions that must fail  ######
            
        with self.assertRaises(H2OStartupError) as err:
            H2OLocalServer._has_compatible_version(
                'java version "1.6.0_45"\n' +
                'Java(TM) SE Runtime Environment (build 1.6.0_45-b06)\n' +
                'Java HotSpot(TM) 64-Bit Server VM (build 20.45-b01, mixed mode)\n'
            )
        assert "Your java is not supported: java version \"1.6.0_45\"; Java(TM) SE Runtime Environment (build 1.6.0_45-b06); Java HotSpot(TM) 64-Bit Server VM (build 20.45-b01, mixed mode)" \
            in str(err.exception)

        with self.assertRaises(H2OStartupError) as err:
            H2OLocalServer._has_compatible_version(
                'java version "1.6.0_45"\n' +
                'Java(TM) SE Runtime Environment (build 1.6.0_45-b06)\n' +
                'Oracle JRockit(R) (build R28.2.7-7-155314-1.6.0_45-20130329-0641-linux-x86_64, compiled mode)\n'
            )
        assert "Your java is not supported: java version \"1.6.0_45\"; Java(TM) SE Runtime Environment (build 1.6.0_45-b06); Oracle JRockit(R) (build R28.2.7-7-155314-1.6.0_45-20130329-0641-linux-x86_64, compiled mode)" \
               in str(err.exception)

        with self.assertRaises(H2OStartupError) as err:
            H2OLocalServer._has_compatible_version(
                'java version "1.6.0_38"\n' +
                'OpenJDK Runtime Environment (IcedTea6 1.13.10) (6b38-1.13.10-1~deb7u1)\n' +
                'OpenJDK 64-Bit Server VM (build 23.25-b01, mixed mode)\n'
            )
        assert "Your java is not supported: java version \"1.6.0_38\"; OpenJDK Runtime Environment (IcedTea6 1.13.10) (6b38-1.13.10-1~deb7u1); OpenJDK 64-Bit Server VM (build 23.25-b01, mixed mode)" \
               in str(err.exception)

        with self.assertRaises(H2OStartupError) as err:
            H2OLocalServer._has_compatible_version(
                'java version "1.7.0_121"\n' +
                'OpenJDK Runtime Environment (IcedTea 2.6.8) (Alpine 7.121.2.6.8-r0)\n' +
                'OpenJDK 64-Bit Server VM (build 24.121-b00, mixed mode)\n'
            )
        assert "Your java is not supported: java version \"1.7.0_121\"; OpenJDK Runtime Environment (IcedTea 2.6.8) (Alpine 7.121.2.6.8-r0); OpenJDK 64-Bit Server VM (build 24.121-b00, mixed mode)" \
               in str(err.exception)

        with self.assertRaises(H2OStartupError) as err:
            H2OLocalServer._has_compatible_version(
                'java version "1.7.0_111"\n' +
                'OpenJDK Runtime Environment (IcedTea 2.6.7) (7u111-2.6.7-2~deb8u1)\n' +
                'OpenJDK 64-Bit Server VM (build 24.111-b01, mixed mode)\n'
            )
        assert "Your java is not supported: java version \"1.7.0_111\"; OpenJDK Runtime Environment (IcedTea 2.6.7) (7u111-2.6.7-2~deb8u1); OpenJDK 64-Bit Server VM (build 24.111-b01, mixed mode)" \
               in str(err.exception)

        with self.assertRaises(H2OStartupError) as err:
            H2OLocalServer._has_compatible_version(
                'java version "1.7.0_121"\n' +
                'OpenJDK Runtime Environment (IcedTea 2.6.8) (Alpine 7.121.2.6.8-r0)\n' +
                'OpenJDK 64-Bit Server VM (build 24.121-b00, mixed mode)\n'
            )
        assert "Your java is not supported: java version \"1.7.0_121\"; OpenJDK Runtime Environment (IcedTea 2.6.8) (Alpine 7.121.2.6.8-r0); OpenJDK 64-Bit Server VM (build 24.121-b00, mixed mode)" \
               in str(err.exception)


        ###### Versions that must pass  ######

        H2OLocalServer._has_compatible_version(
            'java version "13.0.1" 2019-10-15\n' +
            'Java(TM) SE Runtime Environment (build 13.0.1+9)\n' +
            'Java HotSpot(TM) 64-Bit Server VM (build 13.0.1+9, mixed mode, sharing)\n'
        )

        H2OLocalServer._has_compatible_version(
            'java version "1.8.0_181"\n' +
            'Java(TM) SE Runtime Environment (build 1.8.0_181-b13)\n' +
            'Java HotSpot(TM) 64-Bit Server VM (build 25.181-b13, mixed mode)\n'
        )

        H2OLocalServer._has_compatible_version(
            'java version "1.8.0_181"\n' +
            'Java(TM) SE Runtime Environment (build 1.8.0_181-b13)\n' +
            'Java HotSpot(TM) 64-Bit Server VM (build 25.181-b13, mixed mode)\n'
        )

        H2OLocalServer._has_compatible_version(
            'java version "10.0.2" 2018-07-17"\n' +
            'Java(TM) SE Runtime Environment 18.3 (build 10.0.2+13)\n' +
            'Java HotSpot(TM) 64-Bit Server VM 18.3 (build 10.0.2+13, mixed mode)\n'
        )

        H2OLocalServer._has_compatible_version(
            'openjdk version "1.8.0_181"\n' +
            'OpenJDK Runtime Environment (build 1.8.0_181-8u181-b13-1~deb9u1-b13)\n' +
            'OpenJDK 64-Bit Server VM (build 25.181-b13, mixed mode)\n'
        )

        H2OLocalServer._has_compatible_version(
            'openjdk version "9.0.4""\n' +
            'OpenJDK Runtime Environment (build 9.0.4+12-Debian-4)\n' +
            'OpenJDK 64-Bit Server VM (build 9.0.4+12-Debian-4, mixed mode)\n'
        )

        H2OLocalServer._has_compatible_version(
            'openjdk version "10.0.2" 2018-07-17"\n' +
            'OpenJDK Runtime Environment (build 10.0.2+13-Debian-1)\n' +
            'OpenJDK 64-Bit Server VM (build 10.0.2+13-Debian-1, mixed mode)\n'
        )

        H2OLocalServer._has_compatible_version(
            'openjdk version "11" 2018-09-25\n' +
            'OpenJDK Runtime Environment (build 11+24-Debian-1)\n' +
            'OpenJDK 64-Bit Server VM (build 11+24-Debian-1, mixed mode, sharing)\n'
        )


suite = unittest.TestLoader().loadTestsFromTestCase(TestUnsupportedJavaVersionCheck)
unittest.TextTestRunner().run(suite)
