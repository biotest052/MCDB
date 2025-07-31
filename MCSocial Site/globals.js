async function verifyAuthToken() {
    const authToken = localStorage.getItem("mcSocialAuthToken");
    if (authToken !== undefined && authToken !== null) {
        const token = JSON.parse(authToken).authToken;
        const req = await fetch("http://localhost:8000/query", {
            method: "POST",
            headers: {
                'Authorization': getAuthToken()
            },
            body: `auth verify ${token}`
        });

        if (!req.ok) {
            const res = await req.body();
            console.log("Failed to verify auth token: " + res);
        } else {
            const res = await req.json();
            if (!res.tokenValid) {
                removeAuthToken();
            }
        }

    }
}

function removeAuthToken() {
    localStorage.removeItem("mcSocialAuthToken");
}

function hydrateNavBar() {
    const container = document.getElementById("navlinks");

    const authData = localStorage.getItem("mcSocialAuthToken");

    if (authData === null || authData === undefined) {
        container.innerHTML += `
        <li><a href="login.html">Login</a></li>
        <li><a href="register.html">Register</a></li>
        `;



    } else {
        const obj = JSON.parse(authData);
        container.innerHTML += `
        <li><a href="profile.html?id=${obj.userId}">Profile</a></li>
        <li><a href="#" onclick="logOut()">Log Out</a></li>
        `;

    }

}

function getAuthToken() {
    const authToken = localStorage.getItem("mcSocialAuthToken");
    if (authToken !== undefined && authToken !== null) {
        const obj = JSON.parse(authToken);
        return obj.authToken;
    } else return null;
}

function getSignedInId() {
    const authToken = localStorage.getItem("mcSocialAuthToken");
    if (authToken !== undefined && authToken !== null) {
        const obj = JSON.parse(authToken);
        return obj.userId;
    } else return 0;
}

function getUsername() {
    const authToken = localStorage.getItem("mcSocialAuthToken");
    if (authToken !== undefined && authToken !== null) {
        const obj = JSON.parse(authToken);
        return obj.username;
    } else return null;
}

async function getProfilePictureLink(userId) {
    const req = await fetch("http://localhost:8000/query", {
        method: "POST",
        headers: {
            'Authorization': getAuthToken()
        },
        body: `select * from profiles where userId=${userId}`
    })

    if (!req.ok) {
        return null;
    } else {
        const obj = await req.json();
        if (obj.length > 0) {
            return obj[0].profilePic;
        } else return null;
    }

}

async function logOut() {
    const stored = localStorage.getItem("mcSocialAuthToken");
    if (stored !== null && stored !== undefined) {
        const obj = JSON.parse(stored);
        const req = await fetch("http://localhost:8000/query", {
            method: "POST",
            headers: {
                'Authorization': getAuthToken()
            },
            body: `auth logout ${obj.authToken}`
        });

        // doesn't matter what the server says, the user's token will be revoked

    }

    removeAuthToken();
    location.reload();

}

function redirect(path) {
    window.location.href = path;
}

async function init() {
    await verifyAuthToken();
    hydrateNavBar();
}

init();