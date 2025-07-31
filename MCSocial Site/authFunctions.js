async function register() {
    const username = document.getElementById('registerUsername').value;
    const password = document.getElementById('registerPassword').value;
    const confirmPassword = document.getElementById('registerConfirmPassword').value;

    if (password !== confirmPassword) {
        alert("Passwords don't match!");
        return;
    }

    const req = await fetch("http://localhost:8000/query", {
        method: "POST",
        headers: {
            'Authorization': getAuthToken()
        },
        body: `auth register ${username} ${password}`
    })

    if (!req.ok) {
        const messageHolder = document.getElementById("errorMessage");
        messageHolder.innerHTML += await req.text() + "<br/><br/>";
    } else {
        const res = await req.json();
        setAuthTokenCookie({
            username: res.username,
            userId: res.userId,
            authToken: res.authToken
        });
        const blankProfile = {
            username: res.username,
            userDesc: "Just a friendly neighborhood Minecraft player!",
            profilePic: "http://localhost:8000/defaultProfilePic.png"
        }

        await fetch("http://localhost:8000/query", {
            method: "POST",
            headers: {
                'Authorization': res.authToken
            },
            body: `insert into profiles value ${JSON.stringify(blankProfile)}`
        })
        redirect("index.html");
    }

}

async function login() {
    const username = document.getElementById('loginUsername').value;
    const password = document.getElementById('loginPassword').value;

    const req = await fetch("http://localhost:8000/query", {
        method: "POST",
        headers: {
            'Authorization': getAuthToken()
        },
        body: `auth login ${username} ${password}`
    })

    if (!req.ok) {
        const messageHolder = document.getElementById("errorMessage");
        messageHolder.innerHTML += await req.text() + "<br/><br/>";
    } else {
        const res = await req.json();
        setAuthTokenCookie({
            username: res.username,
            userId: res.userId,
            authToken: res.authToken
        })
        redirect("index.html");
    }
}

// obj keys: {username, userId, authToken}
function setAuthTokenCookie(authTokenObj) {

    const expiration = new Date();
    expiration.setTime(expiration.getTime() + 604800000);
    const stringAuthTokenObj = JSON.stringify(authTokenObj);

    localStorage.setItem("mcSocialAuthToken", stringAuthTokenObj);

}